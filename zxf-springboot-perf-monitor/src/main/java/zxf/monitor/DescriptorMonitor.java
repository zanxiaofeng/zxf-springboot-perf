package zxf.monitor;

import com.sun.management.UnixOperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class DescriptorMonitor {
    private final Duration checkInterval;
    private final int openLimit;
    private final ScheduledExecutorService monitorExecutor;

    public DescriptorMonitor(Duration checkInterval, int openLimit) {
        this.checkInterval = checkInterval;
        this.openLimit = openLimit;
        this.monitorExecutor = newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "DescriptorMonitor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        monitorExecutor.scheduleWithFixedDelay(this::checkDescriptors, checkInterval.toSeconds(), checkInterval.toSeconds(), TimeUnit.SECONDS);
    }

    public void stop() {
        monitorExecutor.shutdown();
    }

    private void checkDescriptors() {
        System.out.println("checkDescriptors");
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof UnixOperatingSystemMXBean) {
            UnixOperatingSystemMXBean unix = ((UnixOperatingSystemMXBean) os);
            if (unix.getOpenFileDescriptorCount() > openLimit) {
                System.out.println("⚠️ 文件描述符泄漏: " + unix.getOpenFileDescriptorCount() + " / " + openLimit);
            }
        }
    }
}
