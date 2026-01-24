package zxf.monitor.object;

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
    private final MonitorConfig monitorConfig = new MonitorConfig();
    private final ScheduledExecutorService cleanupExecutor;
    private final ScheduledExecutorService leakDetectionExecutor;
    private final ScheduledExecutorService statsExecutor;
    private MonitorListener<T> listener;

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
     * 启动监控
     */
    public void startup(Consumer<MonitorConfig> configurator, MonitorListener<T> listener) {
        synchronized (this) {
            configurator.accept(monitorConfig);
            this.listener = listener;
        }

        // 定期清理已回收的引用（快速清理，每100毫秒）
        cleanupExecutor.scheduleAtFixedRate(this::cleanupCollectedReferences, 100, 100, TimeUnit.MILLISECONDS);

        // 定期检查泄漏（较慢，每30秒）
        leakDetectionExecutor.scheduleAtFixedRate(this::performLeakDetection, monitorConfig.checkInterval.getSeconds(),
                monitorConfig.checkInterval.getSeconds(), TimeUnit.SECONDS);

        // 定期更新统计数据（每5秒）
        statsExecutor.scheduleAtFixedRate(this::updateStats, monitorConfig.getTatsInterval().getSeconds(),
                monitorConfig.getTatsInterval().getSeconds(), TimeUnit.SECONDS);
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

        TReference<T> ref;
        MonitorListener<T> currentListener;
        synchronized (this) {
            ref = new TReference<>(object, referenceQueue, metadata);
            activeReferences.put(ref.getId(), ref);
            totalCreated.incrementAndGet();
            currentListener = listener;
        }

        // 回调移出同步块，避免死锁
        if (currentListener != null) {
            try {
                currentListener.onObjectRegistered(ref);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
        return ref;
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
        List<TReference<T>> collectedRefs = new ArrayList<>();
        Reference<? extends T> ref;
        MonitorListener<T> currentListener;

        // 第一阶段：收集已回收的引用
        while ((ref = referenceQueue.poll()) != null) {
            @SuppressWarnings("unchecked")
            TReference<T> trackedRef = (TReference<T>) ref;
            trackedRef.markAsCollected();
            collectedRefs.add(trackedRef);
        }

        // 第二阶段：移除并更新统计
        synchronized (this) {
            currentListener = listener;
            for (TReference<T> trackedRef : collectedRefs) {
                if (activeReferences.remove(trackedRef.getId()) != null) {
                    totalCollected.incrementAndGet();
                }
            }
        }

        // 第三阶段：回调移出同步块，避免死锁
        if (currentListener != null) {
            for (TReference<T> trackedRef : collectedRefs) {
                try {
                    currentListener.onObjectCollected(trackedRef);
                } catch (Exception e) {
                    // 忽略监听器异常
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

        // 用于存储需要回调的事件
        List<LeakEvent> leakEvents = new ArrayList<>();
        List<LeakEvent> confirmEvents = new ArrayList<>();
        MonitorListener<T> currentListener;

        // 第一阶段：检测并标记泄漏，收集回调事件
        synchronized (this) {
            currentListener = listener;
            for (TReference<T> ref : activeReferences.values()) {
                if (ref.isActive() && ref.getAge().compareTo(monitorConfig.maxObjectAge) > 0) {
                    String reason = "对象存活时间超过 " + monitorConfig.maxObjectAge;
                    ref.markAsLeakSuspected(reason);
                    totalLeakSuspected.incrementAndGet();
                    leakEvents.add(new LeakEvent(ref, reason));
                }
            }

            MonitorStats currentStats = getStats();
            if (currentStats.totalLeakSuspected() > monitorConfig.leakSuspectThreshold) {
                for (TReference<T> ref : activeReferences.values()) {
                    if (ref.isActive()) {
                        String reason = "Active count exceeds confirmation threshold";
                        ref.markAsLeakConfirmed(reason);
                        totalLeakConfirmed.incrementAndGet();
                        confirmEvents.add(new LeakEvent(ref, reason));
                    }
                }
            }
        }

        // 第二阶段：回调移出同步块，避免死锁
        if (currentListener != null) {
            for (LeakEvent event : leakEvents) {
                try {
                    @SuppressWarnings("unchecked")
                    TReference<T> ref = (TReference<T>) event.ref;
                    currentListener.onLeakSuspected(ref, event.reason);
                } catch (Exception e) {
                    // 忽略监听器异常
                }
            }
            for (LeakEvent event : confirmEvents) {
                try {
                    @SuppressWarnings("unchecked")
                    TReference<T> ref = (TReference<T>) event.ref;
                    currentListener.onLeakConfirmed(ref, event.reason);
                } catch (Exception e) {
                    // 忽略监听器异常
                }
            }
        }
    }

    /**
     * 泄漏事件容器
     */
    private static class LeakEvent {
        final TReference<?> ref;
        final String reason;

        LeakEvent(TReference<?> ref, String reason) {
            this.ref = ref;
            this.reason = reason;
        }
    }

    /**
     * 更新统计
     */
    private void updateStats() {
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
        if (listener != null) {
            try {
                listener.onStatsUpdated(stats);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }
}
