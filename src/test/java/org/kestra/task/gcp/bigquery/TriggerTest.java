package org.kestra.task.gcp.bigquery;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.kestra.core.runners.RunContextFactory;
import org.kestra.core.schedulers.AbstractScheduler;
import org.kestra.core.schedulers.DefaultScheduler;
import org.kestra.core.schedulers.SchedulerExecutionStateInterface;
import org.kestra.core.schedulers.SchedulerTriggerStateInterface;
import org.kestra.core.utils.TestsUtils;
import org.kestra.runner.memory.MemoryFlowListeners;
import org.kestra.task.gcp.gcs.models.Blob;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class TriggerTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private SchedulerTriggerStateInterface triggerState;

    @Inject
    private SchedulerExecutionStateInterface executionState;

    @Inject
    private MemoryFlowListeners flowListenersService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    private RunContextFactory runContextFactory;

    @Value("${kestra.variables.globals.project}")
    private String project;

    @Value("${kestra.variables.globals.dataset}")
    private String dataset;

    @Value("${kestra.variables.globals.table}")
    private String table;

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(TriggerTest.class.getClassLoader().getResource("flows")));
    }

    @Test
    void flow() throws Exception {
        // mock flow listeners
        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (AbstractScheduler scheduler = new DefaultScheduler(
            this.applicationContext,
            this.flowListenersService,
            this.executionState,
            this.triggerState
        )) {
            AtomicReference<Execution> last = new AtomicReference<>();

            // wait for execution
            executionQueue.receive(TriggerTest.class, execution -> {
                last.set(execution);

                queueCount.countDown();
                assertThat(execution.getFlowId(), is("bigquery-listen"));
            });


            Query task = Query.builder()
                .id(QueryTest.class.getSimpleName())
                .type(Query.class.getName())
                .sql("CREATE TABLE `" + project + "." + dataset + "." + table + "` AS (SELECT 1 AS number UNION ALL SELECT 2 AS number)")
                .build();

            scheduler.run();

            task.run(TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of()));

            queueCount.await();

            @SuppressWarnings("unchecked")
            java.util.List<Blob> trigger = (java.util.List<Blob>) last.get().getTrigger().getVariables().get("rows");

            assertThat(trigger.size(), is(2));
        }
    }
}
