package zxf.monitor;

import com.sun.management.UnixOperatingSystemMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

/**
 * 文件描述符监控 - 通过 MXBean 和 /proc/self/fd/ 双重监控
 *
 * @author davis
 */
public class DescriptorMonitor {
    private final Duration checkInterval;
    private final int openLimit;
    private final ScheduledExecutorService monitorExecutor;
    private final boolean detailedMode;
    private final Set<String> filters;

    public DescriptorMonitor(Duration checkInterval, int openLimit) {
        this(checkInterval, openLimit, false, Collections.emptySet());
    }

    public DescriptorMonitor(Duration checkInterval, int openLimit, boolean detailedMode) {
        this(checkInterval, openLimit, detailedMode, Collections.emptySet());
    }

    public DescriptorMonitor(Duration checkInterval, int openLimit, boolean detailedMode, Set<String> filters) {
        this.checkInterval = checkInterval;
        this.openLimit = openLimit;
        this.detailedMode = detailedMode;
        this.filters = filters != null ? filters : Collections.emptySet();
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

        // 1. 保留原有的 MXBean count check
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof UnixOperatingSystemMXBean unix) {
            long count = unix.getOpenFileDescriptorCount();
            if (count > openLimit) {
                System.err.println("⚠️ 文件描述符泄漏: " + count + " / " + openLimit);
            }

            // 2. 详细模式：额外输出 /proc/self/fd 信息
            if (detailedMode) {
                printDetailedInfo(count);
            }
        }
    }

    private void printDetailedInfo(long mxbeanCount) {
        List<FileDescriptorInfo> descriptors = listOpenFileDescriptors();

        System.out.println("=== 打开的文件描述符 [MXBean:" + mxbeanCount + ", Actual:" + descriptors.size() + "] ===");

        if (descriptors.size() > openLimit) {
            System.err.println("⚠️ 文件描述符数量超过阈值: " + descriptors.size() + " > " + openLimit);
        }

        // 按类型分组统计
        Map<FdType, Long> typeStats = descriptors.stream()
                .collect(Collectors.groupingBy(FileDescriptorInfo::type, Collectors.counting()));
        System.out.println("类型统计: " + typeStats);

        // 显示非标准文件描述符
        descriptors.stream()
                .filter(fd -> fd.type() != FdType.ANON_INODE && fd.type() != FdType.PIPE && fd.type() != FdType.SOCKET)
                .forEach(fd -> System.out.println("  " + fd));

        // 检查是否有匹配过滤器的文件
        if (!filters.isEmpty()) {
            List<FileDescriptorInfo> matched = descriptors.stream()
                    .filter(fd -> filters.stream().anyMatch(filter -> fd.target().contains(filter)))
                    .toList();
            if (!matched.isEmpty()) {
                System.out.println("匹配过滤器的文件:");
                matched.forEach(fd -> System.out.println("  " + fd.target()));
            }
        }
    }

    /**
     * 列出当前进程打开的所有文件描述符
     */
    public static List<FileDescriptorInfo> listOpenFileDescriptors() {
        List<FileDescriptorInfo> result = new ArrayList<>();
        Path fdDir = Paths.get("/proc/self/fd");

        if (!Files.exists(fdDir)) {
            return result;
        }

        try (var stream = Files.list(fdDir)) {
            stream.forEach(fdPath -> {
                try {
                    int fd = Integer.parseInt(fdPath.getFileName().toString());
                    Path target = Files.readSymbolicLink(fdPath);
                    result.add(new FileDescriptorInfo(fd, target.toString()));
                } catch (NumberFormatException | IOException e) {
                    // 忽略无法读取的文件描述符
                }
            });
        } catch (IOException e) {
            // 静默处理 - 可能不是 Linux 系统
        }

        result.sort(Comparator.comparingInt(FileDescriptorInfo::fd));
        return result;
    }

    /**
     * 文件描述符信息
     */
    public record FileDescriptorInfo(int fd, String target) {
        /**
         * 获取文件描述符类型
         */
        public FdType type() {
            if (target.startsWith("/")) {
                return FdType.FILE;
            } else if (target.startsWith("socket:")) {
                return FdType.SOCKET;
            } else if (target.startsWith("pipe:")) {
                return FdType.PIPE;
            } else if (target.startsWith("anon_inode:")) {
                return FdType.ANON_INODE;
            } else if (target.startsWith("/dev/")) {
                return FdType.DEVICE;
            } else {
                return FdType.OTHER;
            }
        }

        @Override
        public String toString() {
            return String.format("FD[%d] -> %s (%s)", fd, target, type());
        }
    }

    /**
     * 文件描述符类型枚举
     */
    public enum FdType {
        FILE,       // 普通文件
        SOCKET,     // 网络套接字
        PIPE,       // 管道
        ANON_INODE, // 匿名inode（如epoll）
        DEVICE,     // 设备文件
        OTHER       // 其他类型
    }
}
