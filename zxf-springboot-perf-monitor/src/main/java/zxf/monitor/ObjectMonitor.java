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
    private final ScheduledExecutorService cleanupExecutor;
    private final ScheduledExecutorService leakDetectionExecutor;
    private final ScheduledExecutorService statsExecutor;

    /**
     * 监控数据存储
     */
    private final Map<String, TReference<T>> activeReferences = new ConcurrentHashMap<>();
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
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ObjectMonitor-Cleanup-" + targetClass.getName());
            thread.setDaemon(true);
            return thread;
        });
        this.leakDetectionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ObjectMonitor-LeadDetect-" + targetClass.getName());
            thread.setDaemon(true);
            return thread;
        });
        this.statsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ObjectMonitor-Stats-" + targetClass.getName());
            thread.setDaemon(true);
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
     * 启动监控
     */
    public void startup(Consumer<MonitorConfig> configurator) {
        synchronized (this) {
            configurator.accept(monitorConfig);
        }

        // 定期清理已回收的引用（快速清理，每100毫秒）
        cleanupExecutor.scheduleAtFixedRate(this::cleanupCollectedReferences, 100, 100, TimeUnit.MILLISECONDS);

        // 定期检查泄漏（较慢，每30秒）
        leakDetectionExecutor.scheduleAtFixedRate(this::performLeakDetection, monitorConfig.checkInterval.getSeconds(),
                monitorConfig.checkInterval.getSeconds(), TimeUnit.SECONDS);

        // 定期更新统计数据（每5秒）
        statsExecutor.scheduleAtFixedRate(this::updateStats, monitorConfig.getStatsInterval().getSeconds(),
                monitorConfig.getStatsInterval().getSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 停止监控
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        leakDetectionExecutor.shutdown();
        try {
            leakDetectionExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        statsExecutor.shutdown();
        try {
            statsExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.printf("[%s] 对象监控器已停止%n", targetClass.getName());
    }

    /**
     * 注册对象并附加元数据
     */
    public TReference<T> register(T object, Map<String, Object> metadata) {
        if (!targetClass.isInstance(object)) {
            return null;
        }

        synchronized (this) {
            TReference<T> ref = new TReference<>(object, referenceQueue, metadata);
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
        TReference<T> ref = activeReferences.get(refId);
        if (ref != null) {
            ref.recordAccess();
        }
    }

    /**
     * 更新对象生命周期阶段
     */
    public void updateLifecyclePhase(String refId, String phase) {
        TReference<T> ref = activeReferences.get(refId);
        if (ref != null) {
            ref.updateLifecyclePhase(phase);
        }
    }

    /**
     * 添加元数据
     */
    public void addMetadata(String refId, String key, Object value) {
        TReference<T> ref = activeReferences.get(refId);
        if (ref != null) {
            ref.addMetadata(key, value);
        }
    }

    /**
     * 获取当前统计信息
     */
    public MonitorStats getStats() {
        synchronized (this) {
            long totalAge = activeReferences.values().stream().map(TReference::getAge)
                    .map(Duration::getSeconds).reduce(0L, Long::sum);
            double avgAge = activeReferences.size() > 0 ? (double) totalAge / activeReferences.size() : 0.0;

            return new MonitorStats(targetClass.getName(), activeReferences.size(), totalCreated.get(), totalCollected.get(),
                    totalLeakSuspected.get(), totalLeakConfirmed.get(), avgAge, Instant.now());
        }
    }

    /**
     * 获取疑似泄漏的引用
     */
    public List<TReference<T>> getLeakSuspectedReferences() {
        return activeReferences.values().stream()
                .filter(ref -> ref.isLeakSuspected() || ref.isLeakConfirmed())
                .collect(Collectors.toList());
    }

    /**
     * 清理已回收的引用
     */
    private void cleanupCollectedReferences() {
        Reference<? extends T> ref;
        while ((ref = referenceQueue.poll()) != null) {
            @SuppressWarnings("unchecked")
            TReference<T> trackedRef = (TReference<T>) ref;
            trackedRef.markAsCollected();

            String refId = trackedRef.getId();
            if (activeReferences.remove(refId) != null) {
                totalCollected.incrementAndGet();
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
        System.out.println("performLeakDetection");
        if (monitorConfig.autoGcBeforeCheck) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized (this) {
            for (TReference<T> ref : activeReferences.values()) {
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
                for (TReference<T> ref : activeReferences.values()) {
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
        System.out.println("updateStats");
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
        for (MonitorListener<T> listener : listeners) {
            try {
                listener.onStatsUpdated(stats);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }
}
