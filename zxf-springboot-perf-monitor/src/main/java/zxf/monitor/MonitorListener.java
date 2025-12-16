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
    void onObjectRegistered(TReference<T> ref);

    /**
     * 对象回收
     *
     * @param ref 跟踪引用
     */
    void onObjectCollected(TReference<T> ref);

    /**
     * 对象泄漏
     *
     * @param ref    跟踪引用
     * @param reason 原因
     */
    void onLeakSuspected(TReference<T> ref, String reason);

    /**
     * 确认泄漏
     *
     * @param ref    跟踪引用
     * @param reason 原因
     */
    void onLeakConfirmed(TReference<T> ref, String reason);

    /**
     * 统计数据更新
     *
     * @param stats 统计数据
     */
    void onStatsUpdated(MonitorStats stats);
}
