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
    public Map<String, MonitorStats> getAllStats() {
        Map<String, MonitorStats> allStats = new HashMap<>();
        for (Map.Entry<Class<?>, ObjectMonitor<?>> entry : monitors.entrySet()) {
            ObjectMonitor<?> monitor = entry.getValue();
            MonitorStats stats = monitor.getStats();
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
     * 关闭所有监控器
     */
    public void shutdownAll() {
        for (ObjectMonitor<?> monitor : monitors.values()) {
            monitor.shutdown();
        }
        monitors.clear();
    }
}