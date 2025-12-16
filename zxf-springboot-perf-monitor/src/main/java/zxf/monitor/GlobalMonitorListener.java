package zxf.monitor;

/**
 * 全局监听器接口
 *
 * @author davis
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
