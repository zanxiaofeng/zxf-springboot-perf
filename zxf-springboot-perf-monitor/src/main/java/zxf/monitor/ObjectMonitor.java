package zxf.monitor;

import lombok.Data;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 对象监控器 - 监控特定类对象实例的生命周期
 *
 * @author davis
 */
public class ObjectMonitor<T> {
    /**
     * 监控目标类
     */
    private final Class<T> targetClass;

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
    private final AtomicLong totalExpired = new AtomicLong(0);

    /**
     * 历史数据（用于趋势分析）
     */
    private final ConcurrentLinkedDeque<StatsSnapshot> history = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY_SIZE = 1000;

    /**
     * 配置参数
     */
    private final Config config = new Config();

    /**
     * 执行器
     */
    private ScheduledExecutorService monitorExecutor;
    private ScheduledExecutorService cleanupExecutor;

    /**
     * 读写锁
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 监听器列表
     */
    private final List<MonitorListener<T>> listeners = new CopyOnWriteArrayList<>();

    /**
     * 泄漏检测规则
     */
    private final List<LeakDetectionRule<T>> leakRules = new CopyOnWriteArrayList<>();


    /**
     * 创建对象监控器
     */
    public ObjectMonitor(Class<T> targetClass) {
        this.targetClass = targetClass;

        // 添加默认泄漏检测规则
        addDefaultLeakDetectionRules();

        // 启动监控服务
        startMonitoring();
    }

    /**
     * 配置参数
     */
    @Data
    public static class Config {
        int leakSuspectThreshold = 100; // 泄漏嫌疑阈值
        int leakConfirmThreshold = 1000; // 泄漏确认阈值
        Duration maxObjectAge = Duration.ofHours(1); // 最大对象年龄
        Duration checkInterval = Duration.ofSeconds(30); // 检查间隔
        boolean autoGcBeforeCheck = false; // 检查前自动GC
    }

    /**
     * 统计快照
     */
    private record StatsSnapshot(Instant timestamp, long activeCount, long createdCount,
                                 long collectedCount, long leakSuspectedCount, long leakConfirmedCount) {
    }

    /**
     * 监听器接口
     *
     * @param <T>
     */
    public interface MonitorListener<T> {
        /**
         * 对象注册
         *
         * @param ref 跟踪引用
         */
        void onObjectRegistered(TrackedReference<T> ref);

        /**
         * 对象回收
         *
         * @param ref 跟踪引用
         */
        void onObjectCollected(TrackedReference<T> ref);

        /**
         * 对象泄漏
         *
         * @param ref    跟踪引用
         * @param reason 原因
         */
        void onLeakSuspected(TrackedReference<T> ref, String reason);

        /**
         * 确认泄漏
         *
         * @param ref    跟踪引用
         * @param reason 原因
         */
        void onLeakConfirmed(TrackedReference<T> ref, String reason);

        /**
         * 对象过期
         *
         * @param ref 跟踪引用
         */
        void onObjectExpired(TrackedReference<T> ref);

        /**
         * 统计数据更新
         *
         * @param stats 统计数据
         */
        void onStatsUpdated(Stats stats);
    }

    /**
     * 泄漏检测规则接口
     *
     * @param <T>
     */
    public interface LeakDetectionRule<T> {
        /**
         * 检查规则
         *
         * @param ref   跟踪引用
         * @param stats 统计数据
         * @return 是否满足规则
         */
        boolean check(TrackedReference<T> ref, Stats stats);

        /**
         * 获取规则名称
         *
         * @return 规则名称
         */
        String getRuleName();

        /**
         * 获取规则描述
         *
         * @return 规则描述
         */
        String getDescription();
    }

    /**
     * 统计数据类
     */
    public record Stats(String className, long activeCount, long totalCreated, long totalCollected,
                        long totalLeakSuspected, long totalLeakConfirmed, long totalExpired, double avgObjectAgeSeconds,
                        Instant timestamp) {
        public double getLeakRate() {
            return totalCreated > 0 ? (double) activeCount / totalCreated : 0.0;
        }
    }

    /**
     * 添加默认泄漏检测规则
     */
    private void addDefaultLeakDetectionRules() {
        // 规则1: 对象存活时间过长
        addLeakDetectionRule(new LeakDetectionRule<T>() {
            @Override
            public boolean check(TrackedReference<T> ref, Stats stats) {
                Duration age = ref.getAge();
                return age.compareTo(config.maxObjectAge) > 0;
            }

            @Override
            public String getRuleName() {
                return "MaxAgeRule";
            }

            @Override
            public String getDescription() {
                return "对象存活时间超过 " + config.maxObjectAge;
            }
        });

        // 规则2: 活跃对象数量超过阈值
        addLeakDetectionRule(new LeakDetectionRule<T>() {
            @Override
            public boolean check(TrackedReference<T> ref, Stats stats) {
                return stats.activeCount > config.leakSuspectThreshold;
            }

            @Override
            public String getRuleName() {
                return "CountThresholdRule";
            }

            @Override
            public String getDescription() {
                return "活跃对象数量超过阈值 " + config.leakSuspectThreshold;
            }
        });

        // 规则3: 泄漏率过高
        addLeakDetectionRule(new LeakDetectionRule<T>() {
            @Override
            public boolean check(TrackedReference<T> ref, Stats stats) {
                return stats.totalCreated > 100 && stats.getLeakRate() > 0.8;
            }

            @Override
            public String getRuleName() {
                return "HighLeakRateRule";
            }

            @Override
            public String getDescription() {
                return "对象泄漏率超过 80%";
            }
        });
    }

    /**
     * 注册对象并附加元数据
     */
    public TrackedReference<T> register(T object, Map<String, Object> metadata) {
        if (!targetClass.isInstance(object)) {
            return null;
        }

        lock.writeLock().lock();
        try {
            TrackedReference<T> ref = new TrackedReference<>(object, referenceQueue, metadata);
            activeReferences.put(ref.getId(), ref);
            totalCreated.incrementAndGet();

            // 通知监听器
            for (MonitorListener<T> listener : listeners) {
                try {
                    listener.onObjectRegistered(ref);
                } catch (Exception e) {
                    // 忽略监听器异常
                }
            }

            return ref;
        } finally {
            lock.writeLock().unlock();
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
     * 启动监控服务
     */
    private void startMonitoring() {
        // 创建清理线程池
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ObjectMonitor-Cleanup-" + targetClass.getName());
            thread.setDaemon(true);
            return thread;
        });

        // 定期清理已回收的引用（快速清理，每100毫秒）
        cleanupExecutor.scheduleAtFixedRate(this::cleanupCollectedReferences, 100, 100, TimeUnit.MILLISECONDS);

        // 创建监控线程池
        monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ObjectMonitor-" + targetClass.getName());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });

        // 定期检查泄漏（较慢，每30秒）
        monitorExecutor.scheduleAtFixedRate(this::performLeakDetection, config.checkInterval.getSeconds(), config.checkInterval.getSeconds(), TimeUnit.SECONDS);

        // 定期更新统计数据（每5秒）
        monitorExecutor.scheduleAtFixedRate(this::updateStatsSnapshot, 5, 5, TimeUnit.SECONDS);

        // 定期输出统计信息（每分钟）
        monitorExecutor.scheduleAtFixedRate(this::printStatistics, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 清理已回收的引用
     */
    private void cleanupCollectedReferences() {
        int cleanedCount = 0;

        Reference<? extends T> ref;
        while ((ref = referenceQueue.poll()) != null) {
            if (ref instanceof TrackedReference) {
                @SuppressWarnings("unchecked")
                TrackedReference<T> trackedRef = (TrackedReference<T>) ref;
                trackedRef.markAsCollected();

                String refId = trackedRef.getId();
                if (activeReferences.remove(refId) != null) {
                    totalCollected.incrementAndGet();
                    cleanedCount++;

                    // 通知监听器
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

        if (cleanedCount > 0) {
            updateStatsSnapshot();
        }
    }

    /**
     * 执行泄漏检测
     */
    private void performLeakDetection() {
        if (config.autoGcBeforeCheck) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Stats currentStats = getStats();
        List<TrackedReference<T>> suspects = new ArrayList<>();

        // 应用泄漏检测规则
        lock.readLock().lock();
        try {
            for (TrackedReference<T> ref : activeReferences.values()) {
                if (ref.getState() == TrackedReference.State.ACTIVE) {
                    for (LeakDetectionRule<T> rule : leakRules) {
                        if (rule.check(ref, currentStats)) {
                            ref.markAsLeakSuspected(rule.getDescription());
                            totalLeakSuspected.incrementAndGet();
                            suspects.add(ref);

                            // 通知监听器
                            for (MonitorListener<T> listener : listeners) {
                                try {
                                    listener.onLeakSuspected(ref, rule.getDescription());
                                } catch (Exception e) {
                                    // 忽略监听器异常
                                }
                            }
                            break;
                        }
                    }

                    // 检查是否确认为泄漏
                    if (currentStats.activeCount > config.leakConfirmThreshold) {
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
        } finally {
            lock.readLock().unlock();
        }

        // 清理过期对象
        cleanupExpiredObjects();
    }

    /**
     * 清理过期对象
     */
    private void cleanupExpiredObjects() {
        Instant now = Instant.now();
        List<TrackedReference<T>> expired = new ArrayList<>();

        lock.readLock().lock();
        try {
            for (TrackedReference<T> ref : activeReferences.values()) {
                if (ref.getState() == TrackedReference.State.EXPIRED ||
                        (ref.getState() == TrackedReference.State.LEAK_SUSPECTED &&
                                ref.getIdleTime().toMinutes() > 60)) {
                    expired.add(ref);
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        for (TrackedReference<T> ref : expired) {
            ref.markAsExpired();
            totalExpired.incrementAndGet();

            for (MonitorListener<T> listener : listeners) {
                try {
                    listener.onObjectExpired(ref);
                } catch (Exception e) {
                    // 忽略监听器异常
                }
            }
        }
    }

    /**
     * 更新统计快照
     */
    private void updateStatsSnapshot() {
        Stats stats = getStats();
        history.addFirst(new StatsSnapshot(
                Instant.now(),
                stats.activeCount,
                stats.totalCreated,
                stats.totalCollected,
                stats.totalLeakSuspected,
                stats.totalLeakConfirmed
        ));

        // 保持历史记录大小
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeLast();
        }

        // 通知监听器
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
        Stats stats = getStats();
        System.out.printf(" === [%s] 对象监控统计 ===%n", targetClass.getName());
        System.out.printf("活跃实例: %d%n", stats.activeCount);
        System.out.printf("总计创建: %d%n", stats.totalCreated);
        System.out.printf("已回收: %d (%.1f%%)%n",
                stats.totalCollected,
                stats.totalCreated > 0 ?
                        (double) stats.totalCollected / stats.totalCreated * 100 : 0);
        System.out.printf("疑似泄漏: %d%n", stats.totalLeakSuspected);
        System.out.printf("确认泄漏: %d%n", stats.totalLeakConfirmed);
        System.out.printf("泄漏率: %.1f%%%n", stats.getLeakRate() * 100);
        System.out.printf("平均对象年龄: %.1f秒%n", stats.avgObjectAgeSeconds);

        if (stats.activeCount > config.leakSuspectThreshold) {
            System.err.printf("⚠️ 警告: 活跃对象数量超过阈值 (%d > %d)%n",
                    stats.activeCount, config.leakSuspectThreshold);
        }
    }

    /**
     * 获取当前统计信息
     */
    public Stats getStats() {
        lock.readLock().lock();
        try {
            long active = activeReferences.size();
            long created = totalCreated.get();
            long collected = totalCollected.get();
            long suspected = totalLeakSuspected.get();
            long confirmed = totalLeakConfirmed.get();
            long expired = totalExpired.get();

            // 计算平均对象年龄
            double totalAge = 0;
            for (TrackedReference<T> ref : activeReferences.values()) {
                totalAge += ref.getAge().getSeconds();
            }
            double avgAge = active > 0 ? totalAge / active : 0;

            return new Stats(targetClass.getName(), active, created, collected,
                    suspected, confirmed, expired, avgAge, Instant.now());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取所有活跃引用
     */
    public List<TrackedReference<T>> getActiveReferences() {
        return new ArrayList<>(activeReferences.values());
    }

    /**
     * 获取疑似泄漏的引用
     */
    public List<TrackedReference<T>> getLeakSuspectedReferences() {
        return activeReferences.values().stream()
                .filter(ref -> ref.getState() == TrackedReference.State.LEAK_SUSPECTED ||
                        ref.getState() == TrackedReference.State.LEAK_CONFIRMED)
                .collect(Collectors.toList());
    }

    /**
     * 获取引用详情
     */
    public TrackedReference<T> getReference(String refId) {
        return activeReferences.get(refId);
    }

    /**
     * 获取历史趋势数据
     */
    public List<Stats> getHistory(int limit) {
        return history.stream()
                .limit(limit)
                .map(snapshot -> new Stats(
                        targetClass.getName(),
                        snapshot.activeCount,
                        snapshot.createdCount,
                        snapshot.collectedCount,
                        snapshot.leakSuspectedCount,
                        snapshot.leakConfirmedCount,
                        0, // expired not stored in snapshot
                        0, // avg age not stored
                        snapshot.timestamp
                ))
                .collect(Collectors.toList());
    }

    /**
     * 添加泄漏检测规则
     */
    public void addLeakDetectionRule(LeakDetectionRule<T> rule) {
        leakRules.add(rule);
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
    public void configure(Consumer<Config> configurator) {
        lock.writeLock().lock();
        try {
            configurator.accept(config);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 停止监控
     */
    public void shutdown() {
        if (monitorExecutor != null) {
            monitorExecutor.shutdown();
            try {
                monitorExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.printf("[%s] 对象监控器已停止%n", targetClass.getName());
    }

    /**
     * 强制清理所有引用
     */
    public void forceCleanup() {
        System.gc();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        lock.writeLock().lock();
        try {
            activeReferences.clear();
            updateStatsSnapshot();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
