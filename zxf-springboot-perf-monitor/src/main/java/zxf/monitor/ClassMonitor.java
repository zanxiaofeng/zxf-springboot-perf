package zxf.monitor;

import zxf.util.JCmdInvoker;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class ClassMonitor {
    private static final Pattern pattern = Pattern.compile("^\\s*\\d+:\\s+(\\d+)\\s+(\\d+)\\s+([^\\s]+)");
    private final Duration checkInterval;
    private final String[] searchKeys;
    private final long instanceLimit;
    private final ScheduledExecutorService monitorExecutor;

    public ClassMonitor(Duration checkInterval, String[] searchKeys, long instanceLimit) {
        this.checkInterval = checkInterval;
        this.searchKeys = searchKeys;
        this.instanceLimit = instanceLimit;
        this.monitorExecutor = newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ClassMonitor-" + Arrays.asList(searchKeys));
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        monitorExecutor.scheduleWithFixedDelay(this::checkClasses, checkInterval.toSeconds(), checkInterval.toSeconds(), TimeUnit.SECONDS);
    }

    public void stop() {
        monitorExecutor.shutdown();
    }

    private void checkClasses() {
        System.out.println("checkClasses");
        try {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<String> classHistogram = JCmdInvoker.getClassHistogram();
            for (String classStat : classHistogram) {
                Matcher matcher = pattern.matcher(classStat);
                if (!matcher.find()) {
                    continue;
                }

                long instanceCount = Long.parseLong(matcher.group(1));
                if (instanceCount < instanceLimit) {
                    continue;
                }
                long bytes = Long.parseLong(matcher.group(2));
                String className = matcher.group(3);

                for (String searchKey : searchKeys) {
                    if (className.contains(searchKey)) {
                        System.out.println("⚠️ 类泄漏: " + className + " - " + instanceCount + " instances" + " - " + bytes + " bytes");
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("Error in checkClasses");
        }
    }
}
