package zxf.monitor;

/**
 * 监听器接口
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
    void onStatsUpdated(MonitorStats stats);
}
