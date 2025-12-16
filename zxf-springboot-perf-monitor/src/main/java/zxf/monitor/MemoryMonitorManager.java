package zxf.monitor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存监控管理器 - 全局单例，管理所有对象监控器
 *
 * @author davis
 */
public class MemoryMonitorManager {
    private static final MemoryMonitorManager INSTANCE = new MemoryMonitorManager();
    private final Map<Class<?>, ObjectMonitor<?>> monitors = new ConcurrentHashMap<>();
    private final List<GlobalMonitorListener> globalListeners = new ArrayList<>();

    private MemoryMonitorManager() {
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("关闭所有对象监控器...");
            shutdownAll();
        }));
    }

    /**
     * 获取单例实例
     */
    public static MemoryMonitorManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取或创建指定类的监控器
     */
    @SuppressWarnings("unchecked")
    public <T> ObjectMonitor<T> getMonitor(Class<T> clazz) {
        return (ObjectMonitor<T>) monitors.computeIfAbsent(clazz, k -> new ObjectMonitor<>(clazz));
    }

    /**
     * 注册对象并附加元数据
     */
    public <T> TrackedReference<T> monitorObject(T object, Map<String, Object> metadata) {
        if (object == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) object.getClass();
        ObjectMonitor<T> monitor = getMonitor(clazz);
        return monitor.register(object, metadata);
    }

    /**
     * 获取所有监控器的统计信息
     */
    public Map<String, ObjectMonitor.Stats> getAllStats() {
        Map<String, ObjectMonitor.Stats> allStats = new HashMap<>();
        for (Map.Entry<Class<?>, ObjectMonitor<?>> entry : monitors.entrySet()) {
            ObjectMonitor<?> monitor = entry.getValue();
            ObjectMonitor.Stats stats = monitor.getStats();
            allStats.put(stats.className(), stats);
        }
        return allStats;
    }

    /**
     * 获取疑似泄漏的对象
     */
    public Map<String, List<? extends TrackedReference<?>>> getAllLeakSuspectedObjects() {
        Map<String, List<? extends TrackedReference<?>>> leaks = new HashMap<>();
        for (Map.Entry<Class<?>, ObjectMonitor<?>> entry : monitors.entrySet()) {
            ObjectMonitor<?> monitor = entry.getValue();
            List<? extends TrackedReference<?>> leakRefs = monitor.getLeakSuspectedReferences();
            if (!leakRefs.isEmpty()) {
                leaks.put(monitor.getStats().className(), leakRefs);
            }
        }
        return leaks;
    }

    /**
     * 打印全局统计
     */
    public void printGlobalStatistics() {
        Map<String, ObjectMonitor.Stats> allStats = getAllStats();
        System.out.println(" === 全局对象监控统计 ===");

        allStats.forEach((className, stats) -> {
            System.out.printf("%n[%s]%n", className);
            System.out.printf(" 活跃: %d, 总计: %d, 泄漏率: %.1f%%%n",
                    stats.activeCount(),
                    stats.totalCreated(),
                    stats.getLeakRate() * 100);
        });

        // 检测全局内存泄漏
        detectGlobalMemoryLeaks();
    }

    /**
     * 检测全局内存泄漏
     */
    private void detectGlobalMemoryLeaks() {
        Map<String, ObjectMonitor.Stats> allStats = getAllStats();
        boolean hasLeaks = false;

        for (ObjectMonitor.Stats stats : allStats.values()) {
            if (stats.getLeakRate() > 0.9 && stats.totalCreated() > 100) {
                System.err.printf("⚠️ 严重内存泄漏: %s (泄漏率: %.1f%%)%n",
                        stats.className(), stats.getLeakRate() * 100);
                hasLeaks = true;
            }
        }

        if (!hasLeaks) {
            System.out.println("✓ 未检测到严重内存泄漏");
        }
    }

    /**
     * 关闭所有监控器
     */
    public void shutdownAll() {
        for (ObjectMonitor<?> monitor : monitors.values()) {
            monitor.shutdown();
        }
        monitors.clear();
    }

    /**
     * 全局监听器接口
     */
    public interface GlobalMonitorListener {
        /**
         * 当监控器创建时调用
         *
         * @param clazz   监控对象的类
         * @param monitor 监控器实例
         */
        void onMonitorCreated(Class<?> clazz, ObjectMonitor<?> monitor);

        /**
         * 当检测到全局内存泄漏时调用
         *
         * @param className 泄漏的对象类名
         * @param leakCount 泄漏的对象数量
         */
        void onGlobalLeakDetected(String className, int leakCount);
    }

    /**
     * 添加全局监听器
     */
    public void addGlobalListener(GlobalMonitorListener listener) {
        globalListeners.add(listener);
    }
}