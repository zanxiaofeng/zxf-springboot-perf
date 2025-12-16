package zxf.monitor;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 对象监控器 - 监控特定类对象实例的生命周期
 *
 * @author davis
 */
public class ObjectMonitor<T> {
    private final Class<T> targetClass;
    private final List<MonitorListener<T>> listeners = new CopyOnWriteArrayList<>();
    private final MonitorConfig monitorConfig = new MonitorConfig();
    private final ScheduledExecutorService monitorExecutor;
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * 监控数据存储
     */
    private final Map<String, TrackedReference<T>> activeReferences = new ConcurrentHashMap<>();
    private final ReferenceQueue<T> referenceQueue = new ReferenceQueue<>();

    /**
     * 统计数据
     */
    private final AtomicLong totalCreated = new AtomicLong(0);
    private final AtomicLong totalCollected = new AtomicLong(0);
    private final AtomicLong totalLeakSuspected = new AtomicLong(0);
    private final AtomicLong totalLeakConfirmed = new AtomicLong(0);

    /**
     * 创建对象监控器
     */
    public ObjectMonitor(Class<T> targetClass) {
        this.targetClass = targetClass;
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ObjectMonitor-Cleanup-" + targetClass.getName());
            thread.setDaemon(true);
            return thread;
        });
        monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ObjectMonitor-Monitor-" + targetClass.getName());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    /**
     * 添加监听器
     */
    public void addListener(MonitorListener<T> listener) {
        listeners.add(listener);
    }

    /**
     * 移除监听器
     */
    public void removeListener(MonitorListener<T> listener) {
        listeners.remove(listener);
    }

    /**
     * 设置配置
     */
    public void configure(Consumer<MonitorConfig> configurator) {
        synchronized (this) {
            configurator.accept(monitorConfig);
        }
    }

    /**
     * 启动监控
     */
    public void startup() {
        // 定期清理已回收的引用（快速清理，每100毫秒）
        cleanupExecutor.scheduleAtFixedRate(this::cleanupCollectedReferences, 100, 100, TimeUnit.MILLISECONDS);

        // 定期检查泄漏（较慢，每30秒）
        monitorExecutor.scheduleAtFixedRate(this::performLeakDetection, monitorConfig.checkInterval.getSeconds(),
                monitorConfig.checkInterval.getSeconds(), TimeUnit.SECONDS);

        // 定期更新统计数据（每5秒）
        monitorExecutor.scheduleAtFixedRate(this::updateStats, 5, 5, TimeUnit.SECONDS);

        // 定期输出统计信息（每分钟）
        monitorExecutor.scheduleAtFixedRate(this::printStatistics, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 停止监控
     */
    public void shutdown() {
        monitorExecutor.shutdown();
        try {
            monitorExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        cleanupExecutor.shutdown();
        try {
            cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.printf("[%s] 对象监控器已停止%n", targetClass.getName());
    }

    /**
     * 注册对象并附加元数据
     */
    public TrackedReference<T> register(T object, Map<String, Object> metadata) {
        if (!targetClass.isInstance(object)) {
            return null;
        }

        synchronized (this) {
            TrackedReference<T> ref = new TrackedReference<>(object, referenceQueue, metadata);
            activeReferences.put(ref.getId(), ref);
            totalCreated.incrementAndGet();
            for (MonitorListener<T> listener : listeners) {
                try {
                    listener.onObjectRegistered(ref);
                } catch (Exception e) {
                    // 忽略监听器异常
                }
            }
            return ref;
        }
    }

    /**
     * 记录对象访问
     */
    public void recordAccess(String refId) {
        TrackedReference<T> ref = activeReferences.get(refId);
        if (ref != null) {
            ref.recordAccess();
        }
    }

    /**
     * 更新对象生命周期阶段
     */
    public void updateLifecyclePhase(String refId, String phase) {
        TrackedReference<T> ref = activeReferences.get(refId);
        if (ref != null) {
            ref.updateLifecyclePhase(phase);
        }
    }

    /**
     * 添加元数据
     */
    public void addMetadata(String refId, String key, Object value) {
        TrackedReference<T> ref = activeReferences.get(refId);
        if (ref != null) {
            ref.addMetadata(key, value);
        }
    }

    /**
     * 获取当前统计信息
     */
    public MonitorStats getStats() {
        synchronized (this) {
            long totalAge = activeReferences.values().stream().map(TrackedReference::getAge)
                    .map(Duration::getSeconds).reduce(0L, Long::sum);
            double avgAge = activeReferences.size() > 0 ? (double) totalAge / activeReferences.size() : 0.0;

            return new MonitorStats(targetClass.getName(), activeReferences.size(), totalCreated.get(), totalCollected.get(),
                    totalLeakSuspected.get(), totalLeakConfirmed.get(), avgAge, Instant.now());
        }
    }

    /**
     * 获取疑似泄漏的引用
     */
    public List<TrackedReference<T>> getLeakSuspectedReferences() {
        return activeReferences.values().stream()
                .filter(ref -> ref.isLeakSuspected() || ref.isLeakConfirmed())
                .collect(Collectors.toList());
    }

    /**
     * 清理已回收的引用
     */
    private void cleanupCollectedReferences() {
        int cleanedCount = 0;

        Reference<? extends T> ref;
        while ((ref = referenceQueue.poll()) != null) {
            @SuppressWarnings("unchecked")
            TrackedReference<T> trackedRef = (TrackedReference<T>) ref;
            trackedRef.markAsCollected();

            String refId = trackedRef.getId();
            if (activeReferences.remove(refId) != null) {
                totalCollected.incrementAndGet();
                cleanedCount++;
                for (MonitorListener<T> listener : listeners) {
                    try {
                        listener.onObjectCollected(trackedRef);
                    } catch (Exception e) {
                        // 忽略监听器异常
                    }
                }
            }
        }
    }

    /**
     * 执行泄漏检测
     */
    private void performLeakDetection() {
        if (monitorConfig.autoGcBeforeCheck) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized (this) {
            for (TrackedReference<T> ref : activeReferences.values()) {
                if (ref.isActive()) {
                    if (ref.getAge().compareTo(monitorConfig.maxObjectAge) > 0) {
                        ref.markAsLeakSuspected("对象存活时间超过 " + monitorConfig.maxObjectAge);
                        totalLeakSuspected.incrementAndGet();
                        for (MonitorListener<T> listener : listeners) {
                            try {
                                listener.onLeakSuspected(ref, "对象存活时间超过 " + monitorConfig.maxObjectAge);
                            } catch (Exception e) {
                                // 忽略监听器异常
                            }
                        }
                    }
                    continue;
                }
            }

            MonitorStats currentStats = getStats();
            if (currentStats.totalLeakSuspected() > monitorConfig.leakSuspectThreshold) {
                for (TrackedReference<T> ref : activeReferences.values()) {
                    if (!ref.isActive()) {
                        continue;
                    }

                    ref.markAsLeakConfirmed("Active count exceeds confirmation threshold");
                    totalLeakConfirmed.incrementAndGet();
                    for (MonitorListener<T> listener : listeners) {
                        try {
                            listener.onLeakConfirmed(ref, "Active count exceeds confirmation threshold");
                        } catch (Exception e) {
                            // 忽略监听器异常
                        }
                    }
                }
            }
        }
    }

    /**
     * 更新统计
     */
    private void updateStats() {
        MonitorStats stats = getStats();
        for (MonitorListener<T> listener : listeners) {
            try {
                listener.onStatsUpdated(stats);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }

    /**
     * 打印统计信息
     */
    private void printStatistics() {
        MonitorStats stats = getStats();
        System.out.printf(" === [%s] 对象监控统计 ===%n", targetClass.getName());
        System.out.printf("活跃实例: %d%n", stats.activeCount());
        System.out.printf("总计创建: %d%n", stats.totalCreated());
        System.out.printf("已回收: %d (%.1f%%)%n",
                stats.totalCollected(),
                stats.totalCreated() > 0 ?
                        (double) stats.totalCollected() / stats.totalCreated() * 100 : 0);
        System.out.printf("疑似泄漏: %d%n", stats.totalLeakSuspected());
        System.out.printf("确认泄漏: %d%n", stats.totalLeakConfirmed());
        System.out.printf("泄漏率: %.1f%%%n", stats.getLeakRate() * 100);
        System.out.printf("平均对象年龄: %.1f秒%n", stats.avgObjectAgeSeconds());

        if (stats.activeCount() > monitorConfig.leakSuspectThreshold) {
            System.err.printf("⚠️ 警告: 活跃对象数量超过阈值 (%d > %d)%n", stats.activeCount(), monitorConfig.leakSuspectThreshold);
        }
    }
}
