package zxf.monitor;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.*;

public class ThreadMonitor {
    private final Duration checkInterval;
    private final String[] searchKeys;
    private final int foundLimit;
    private final ScheduledExecutorService monitorExecutor;

    public ThreadMonitor(Duration checkInterval, String[] searchKeys, int foundLimit) {
        this.checkInterval = checkInterval;
        this.searchKeys = searchKeys;
        this.foundLimit = foundLimit;
        this.monitorExecutor = newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ThreadMonitor-" + Arrays.asList(searchKeys));
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        monitorExecutor.scheduleWithFixedDelay(this::checkThreads, checkInterval.toSeconds(), checkInterval.toSeconds(), TimeUnit.SECONDS);
    }

    public void stop() {
        monitorExecutor.shutdown();
    }

    private void checkThreads() {
        System.out.println("checkThreads");
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads = threadBean.dumpAllThreads(false, false);

        int totalCount = threads.length;
        List<ThreadInfo> foundThreads = new ArrayList<>();
        for (ThreadInfo thread : threads) {
            String threadInfo = thread + " - " + Arrays.asList(thread.getStackTrace());
            for (String searchKey : searchKeys) {
                if (!threadInfo.contains("ThreadMonitor-") && !threadInfo.contains("ClassMonitor-")
                        && threadInfo.contains(searchKey)) {
                    foundThreads.add(thread);
                    break;
                }
            }
        }
        if (foundThreads.size() > foundLimit) {
            System.out.println("⚠️ 线程泄漏: " + foundThreads.size() + " / " + totalCount);
            for (ThreadInfo thread : foundThreads) {
                System.out.println("⚠️ 线程泄漏: " + thread + Arrays.asList(thread.getStackTrace()));
            }
        }
    }
}