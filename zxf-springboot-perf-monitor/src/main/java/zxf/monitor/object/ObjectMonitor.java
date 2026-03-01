package zxf.monitor.object;

import lombok.extern.slf4j.Slf4j;

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
@Slf4j
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
            Thread thread = new Thread(r, "ObjectMonitor-LeakDetect-" + targetClass.getName());
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
        statsExecutor.scheduleAtFixedRate(this::updateStats, monitorConfig.getStatsInterval().getSeconds(),
                monitorConfig.getStatsInterval().getSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 停止监控
     */
    public void shutdown() {
        shutdownExecutor(cleanupExecutor, "Cleanup");
        shutdownExecutor(leakDetectionExecutor, "LeakDetect");
        shutdownExecutor(statsExecutor, "Stats");
        log.info("[{}] 对象监控器已停止", targetClass.getName());
    }

    private void shutdownExecutor(ScheduledExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("[{}] {} executor did not terminate gracefully", targetClass.getName(), name);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
            return computeStats();
        }
    }

    /**
     * 计算统计信息（调用方必须持有锁）
     */
    private MonitorStats computeStats() {
        long totalAge = activeReferences.values().stream().map(TReference::getAge)
                .map(Duration::getSeconds).reduce(0L, Long::sum);
        double avgAge = activeReferences.size() > 0 ? (double) totalAge / activeReferences.size() : 0.0;

        return new MonitorStats(targetClass.getName(), activeReferences.size(), totalCreated.get(), totalCollected.get(),
                totalLeakSuspected.get(), totalLeakConfirmed.get(), avgAge, Instant.now());
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

            MonitorStats currentStats = computeStats();
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
                    TReference<T> typedRef = (TReference<T>) event.ref;
                    currentListener.onLeakSuspected(typedRef, event.reason);
                } catch (Exception e) {
                    // 忽略监听器异常
                }
            }
            for (LeakEvent event : confirmEvents) {
                try {
                    @SuppressWarnings("unchecked")
                    TReference<T> typedRef = (TReference<T>) event.ref;
                    currentListener.onLeakConfirmed(typedRef, event.reason);
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
        log.info("[{}] 对象监控统计 - 活跃: {}, 创建: {}, 回收: {} ({}%), 疑似泄漏: {}, 确认泄漏: {}, 泄漏率: {}%, 平均年龄: {}秒",
                targetClass.getName(),
                stats.activeCount(), stats.totalCreated(), stats.totalCollected(),
                String.format("%.1f", stats.totalCreated() > 0 ? (double) stats.totalCollected() / stats.totalCreated() * 100 : 0),
                stats.totalLeakSuspected(), stats.totalLeakConfirmed(),
                String.format("%.1f", stats.getLeakRate() * 100),
                String.format("%.1f", stats.avgObjectAgeSeconds()));

        if (stats.activeCount() > monitorConfig.leakSuspectThreshold) {
            log.warn("[{}] 活跃对象数量超过阈值 ({} > {})",
                    targetClass.getName(), stats.activeCount(), monitorConfig.leakSuspectThreshold);
        }

        MonitorListener<T> currentListener;
        synchronized (this) {
            currentListener = listener;
        }
        if (currentListener != null) {
            try {
                currentListener.onStatsUpdated(stats);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }
}
