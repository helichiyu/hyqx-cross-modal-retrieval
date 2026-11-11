package com.hsmty.cmr.domain.task;

import com.hsmty.cmr.config.CmrProperties;
import com.hsmty.cmr.domain.task.entity.CmrTask;
import com.hsmty.cmr.operator.OperatorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 任务执行器：虚拟线程池 + 建库并发闸（默认 1）+ 引擎侧失败自动重试 ≤N（裁决 11/14）
 *
 * <p>数据侧失败不重试直接隔离；任务体抛出的非算子异常按引擎侧处理（意外错误，重试有意义）。
 *
 * @author cmr
 * @since 2026-11-03
 */
@Slf4j
// 显式命名，避免与平台 ThreadTaskConfiguration 的 taskExecutor 冲突
@Component("cmrTaskExecutor")
@RequiredArgsConstructor
public class TaskExecutor {

    private final TaskService taskService;
    private final CmrProperties properties;

    private final ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor();
    private volatile Semaphore buildSlots;

    /** 异步提交（build/eval） */
    public void submit(CmrTask task, TaskBody body) {
        virtual.submit(() -> runWithRetry(task, body));
    }

    /** 同步执行带重试（在线检索用，调用线程内跑完并返回） */
    public void runSyncWithRetry(CmrTask task, TaskBody body) {
        runWithRetry(task, body);
    }

    private void runWithRetry(CmrTask task, TaskBody body) {
        boolean isBuild = CmrTask.TYPE_BUILD.equals(task.getType());
        if (isBuild) {
            buildSlots().acquireUninterruptibly();
        }
        try {
            int retryMax = properties.getBuild().getRetryMax();
            int attempt = 0;
            while (true) {
                try {
                    taskService.markRunning(task);
                    body.run(task);
                    taskService.succeed(task);
                    return;
                } catch (Exception e) {
                    OperatorException oe = e instanceof OperatorException o ? o
                            : new OperatorException(OperatorException.Side.ENGINE, e.getMessage(), e);
                    boolean willRetry = oe.engineSide() && attempt < retryMax;
                    taskService.fail(task, oe.getSide(), oe.getMessage(), willRetry, attempt + 1);
                    log.error("任务失败 task={} step={} side={} retry={} : {}",
                            task.getBizNo(), task.getCurrentStep(), oe.getSide(), willRetry, oe.getMessage(), e);
                    if (!willRetry) {
                        return;
                    }
                    attempt++;
                    sleep(5000L * attempt);
                }
            }
        } finally {
            if (isBuild) {
                buildSlots().release();
            }
        }
    }

    private Semaphore buildSlots() {
        if (buildSlots == null) {
            synchronized (this) {
                if (buildSlots == null) {
                    buildSlots = new Semaphore(Math.max(properties.getBuild().getConcurrency(), 1));
                }
            }
        }
        return buildSlots;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 任务体：一个完整业务链路（如建库 401→406） */
    @FunctionalInterface
    public interface TaskBody {
        void run(CmrTask task) throws Exception;
    }
}
