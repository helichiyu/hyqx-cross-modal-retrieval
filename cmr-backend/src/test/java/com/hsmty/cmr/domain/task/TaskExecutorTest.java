package com.hsmty.cmr.domain.task;

import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.operator.OperatorException;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TaskExecutorTest {

    private final TaskService taskService = mock(TaskService.class);
    private final CmrProperties properties = new CmrProperties();

    @Test
    void successfulTaskIsMarkedDone() {
        TaskExecutor executor = new TaskExecutor(taskService, properties);
        CmrTask task = task();

        executor.runSyncWithRetry(task, ignored -> { });

        verify(taskService).markRunning(task);
        verify(taskService).succeed(task);
        verify(taskService, never()).fail(eq(task), eq(OperatorException.Side.DATA), contains("bad"), eq(false), eq(1));
    }

    @Test
    void dataSideFailureIsNotRetried() {
        TaskExecutor executor = new TaskExecutor(taskService, properties);
        CmrTask task = task();

        executor.runSyncWithRetry(task, ignored -> {
            throw new OperatorException(OperatorException.Side.DATA, "bad input");
        });

        verify(taskService).fail(eq(task), eq(OperatorException.Side.DATA), contains("bad input"), eq(false), eq(1));
        verify(taskService, never()).succeed(task);
    }

    @Test
    void engineFailureIsIsolatedWhenRetryBudgetIsZero() {
        properties.getBuild().setRetryMax(0);
        TaskExecutor executor = new TaskExecutor(taskService, properties);
        CmrTask task = task();

        executor.runSyncWithRetry(task, ignored -> {
            throw new OperatorException(OperatorException.Side.ENGINE, "timeout");
        });

        verify(taskService).fail(eq(task), eq(OperatorException.Side.ENGINE), contains("timeout"), eq(false), eq(1));
        verify(taskService, never()).succeed(task);
    }

    private CmrTask task() {
        CmrTask task = new CmrTask();
        task.setType(CmrTask.TYPE_EVAL);
        task.setBizNo("TASK_TEST");
        task.setCurrentStep("eval");
        return task;
    }
}