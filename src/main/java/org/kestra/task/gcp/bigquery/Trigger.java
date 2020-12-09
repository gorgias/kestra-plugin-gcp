package org.kestra.task.gcp.bigquery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.ExecutionTrigger;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.models.triggers.PollingTriggerInterface;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.models.triggers.TriggerOutput;
import org.kestra.core.runners.RunContext;
import org.kestra.core.utils.IdUtils;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Wait for query on BigQuery"
)
@Plugin(
    examples = {
        @Example(
            title = "Wait for a sql query to return results and iterate through rows",
            full = true,
            code = {
                "id: bigquery-listen",
                "namespace: org.kestra.tests",
                "",
                "tasks:",
                "  - id: each",
                "    type: org.kestra.core.tasks.flows.EachSequential",
                "    tasks:",
                "      - id: return",
                "        type: org.kestra.core.tasks.debugs.Return",
                "        format: \"{{json taskrun.value}}\"",
                "    value: \"{{ trigger.rows }}\"",
                "",
                "triggers:",
                "  - id: watch",
                "    type: org.kestra.task.gcp.bigquery.Trigger",
                "    interval: \"PT5M\"",
                "    sql: \"SELECT * FROM `myproject.mydataset.mytable`\""
            }
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Query.Output>, QueryInterface {
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    protected String projectId;
    protected String serviceAccount;
    @Builder.Default
    protected java.util.List<String> scopes = Collections.singletonList("https://www.googleapis.com/auth/cloud-platform");

    private String sql;

    @Builder.Default
    private boolean legacySql = false;

    @Builder.Default
    private boolean fetch = false;

    @Builder.Default
    private boolean store = false;

    @Builder.Default
    private boolean fetchOne = false;

    @Override
    public Optional<Execution> evaluate(RunContext runContext, TriggerContext context) throws Exception {
        Logger logger = runContext.logger();

        Query task = Query.builder()
            .id(this.id)
            .type(Query.class.getName())
            .projectId(this.projectId)
            .serviceAccount(this.serviceAccount)
            .scopes(this.scopes)
            .sql(this.sql)
            .legacySql(this.legacySql)
            .fetch(this.fetch)
            .store(this.store)
            .fetchOne(this.fetchOne)
            .build();
        Query.Output run = task.run(runContext);

        logger.debug("Found '{}' rows from '{}'", run.getSize(), runContext.render(this.sql));

        if (run.getSize() == 0) {
            return Optional.empty();
        }

        String executionId = IdUtils.create();

        ExecutionTrigger executionTrigger = ExecutionTrigger.of(
            this,
            run
        );

        Execution execution = Execution.builder()
            .id(executionId)
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .flowRevision(context.getFlowRevision())
            .state(new State())
            .trigger(executionTrigger)
            .build();

        return Optional.of(execution);
    }
}
