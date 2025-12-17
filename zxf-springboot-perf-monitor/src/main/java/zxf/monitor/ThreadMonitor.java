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
    private final Boolean logFound;
    private final ScheduledExecutorService monitorExecutor;

    public ThreadMonitor(String[] searchKeys, Boolean logFound) {
        this.searchKeys = searchKeys;
        this.logFound = logFound;
        this.monitorExecutor = newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ThreadMonitor-" + Arrays.asList(searchKeys));
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        monitorExecutor.scheduleWithFixedDelay(this::checkHttpClientThreads, 30, 30, TimeUnit.SECONDS);
    }

    public void stop() {
        monitorExecutor.shutdown();
    }

    private void checkHttpClientThreads() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads = threadBean.dumpAllThreads(false, false);

        int totalCount = threads.length;
        int foundCount = 0;
        for (ThreadInfo thread : threads) {
            String threadInfo = thread + " - " + Arrays.asList(thread.getStackTrace());
            for (String searchKey : searchKeys) {
                if (threadInfo.contains(searchKey)) {
                    foundCount++;
                    if (logFound) {
                        System.out.println("⚠️ 线程泄漏: " + thread + Arrays.asList(thread.getStackTrace()));
                    }
                    break;
                }
            }
        }
        if (foundCount > 0) {
            System.out.println("⚠️ 线程泄漏: " + foundCount + " / " + totalCount);
        }
    }
}