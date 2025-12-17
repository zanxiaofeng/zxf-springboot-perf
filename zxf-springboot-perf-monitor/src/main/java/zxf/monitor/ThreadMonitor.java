package zxf.monitor;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.*;

public class ThreadMonitor {
    private final String[] searchKeys;
    private final ScheduledExecutorService monitorExecutor;

    public ThreadMonitor(String[] searchKeys) {
        this.searchKeys = searchKeys;
        this.monitorExecutor = newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ThreadMonitor-" + Arrays.asList(searchKeys));
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        monitorExecutor.scheduleWithFixedDelay(this::checkHttpClientThreads, 10, 10, TimeUnit.SECONDS);
    }

    public void stop() {
        monitorExecutor.shutdown();
    }

    private void checkHttpClientThreads() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads = threadBean.dumpAllThreads(false, false);

        for (ThreadInfo thread : threads) {
            for (String searchKey : searchKeys) {
                if (thread.getThreadName().contains(searchKey)) {
                    System.out.println("⚠️ 线程泄漏: " + thread + Arrays.asList(thread.getStackTrace()).toString());
                }
            }
        }
    }
}