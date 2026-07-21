package zxf.monitor.object;

/**
 * 监听器接口（所有方法均为空默认实现，按需重写）
 *
 * @param <T>
 * @author davis
 */
public interface MonitorListener<T> {
    /**
     * 对象注册
     *
     * @param ref 跟踪引用
     */
    default void onObjectRegistered(TReference<T> ref) {
    }

    /**
     * 对象回收
     *
     * @param ref 跟踪引用
     */
    default void onObjectCollected(TReference<T> ref) {
    }

    /**
     * 对象泄漏
     *
     * @param ref    跟踪引用
     * @param reason 原因
     */
    default void onLeakSuspected(TReference<T> ref, String reason) {
    }

    /**
     * 确认泄漏
     *
     * @param ref    跟踪引用
     * @param reason 原因
     */
    default void onLeakConfirmed(TReference<T> ref, String reason) {
    }

    /**
     * 统计数据更新
     *
     * @param stats 统计数据
     */
    default void onStatsUpdated(MonitorStats stats) {
    }
}
