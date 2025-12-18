package zxf.monitor.object;


import lombok.Getter;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

/**
 * 追踪引用 - 包含对象的额外元数据
 *
 * @author davis
 */
public class TReference<T> extends WeakReference<T> {
    @Getter
    private final String id;
    @Getter
    private final String className;
    @Getter
    private final Instant creationTime;
    @Getter
    private final Map<String, Object> metadata;
    @Getter
    private volatile State state;
    @Getter
    private volatile Instant lastAccessTime;
    @Getter
    private volatile String lifecyclePhase;

    public TReference(T referent, ReferenceQueue<? super T> queue, Map<String, Object> additionalMetadata) {
        super(referent, queue);
        this.id = UUID.randomUUID().toString();
        this.className = referent.getClass().getName();
        this.creationTime = Instant.now();
        this.state = State.ACTIVE;
        this.lastAccessTime = Instant.now();
        this.lifecyclePhase = "created";
        this.metadata = new HashMap<>();
        this.metadata.put("hashCode", System.identityHashCode(referent));
        this.metadata.put("threadName", Thread.currentThread().getName());
        this.metadata.put("stackTrace", List.of(Thread.currentThread().getStackTrace()));
        if (additionalMetadata != null) {
            this.metadata.putAll(additionalMetadata);
        }
    }

    /**
     * 对象状态枚举
     *
     * @author davis
     */
    public enum State {
        /**
         * 活跃
         */
        ACTIVE,
        /**
         * 疑似泄漏
         */
        LEAK_SUSPECTED,
        /**
         * 确认泄漏
         */
        LEAK_CONFIRMED,
        /**
         * 已回收
         */
        GARBAGE_COLLECTED
    }

    /**
     * 计算对象年龄
     *
     * @return 年龄
     */
    public Duration getAge() {
        return Duration.between(creationTime, Instant.now());
    }

    /**
     * 计算空闲时间
     *
     * @return 空闲时间
     */
    public Duration getIdleTime() {
        return Duration.between(lastAccessTime, Instant.now());
    }

    /**
     * 记录访问
     */
    public void recordAccess() {
        this.lastAccessTime = Instant.now();
    }

    /**
     * 更新生命周期阶段
     */
    public void updateLifecyclePhase(String phase) {
        this.lifecyclePhase = phase;
        metadata.put("lifecyclePhase", phase);
        metadata.put("phaseUpdateTime", Instant.now().toString());
        recordAccess();
    }

    /**
     * 添加或更新元数据
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * 是否是活跃
     *
     * @return true 是，false 否
     */
    public Boolean isActive() {
        return this.state == State.ACTIVE;
    }

    /**
     * 是否是疑似泄漏
     *
     * @return true 是，false 否
     */
    public Boolean isLeakSuspected() {
        return this.state == State.LEAK_SUSPECTED;
    }

    /**
     * 标记为疑似泄漏
     */
    public void markAsLeakSuspected(String reason) {
        this.state = State.LEAK_SUSPECTED;
        metadata.put("leakSuspectedReason", reason);
        metadata.put("leakSuspectedTime", Instant.now().toString());
    }

    /**
     * 是否是疑似泄漏
     *
     * @return true 是，false 否
     */
    public Boolean isLeakConfirmed() {
        return this.state == State.LEAK_CONFIRMED;
    }

    /**
     * 标记为确认泄漏
     */
    public void markAsLeakConfirmed(String reason) {
        this.state = State.LEAK_CONFIRMED;
        metadata.put("leakConfirmedReason", reason);
        metadata.put("leakConfirmedTime", Instant.now().toString());
    }


    /**
     * 是否是已回收
     *
     * @return true 是，false 否
     */
    public Boolean isCollected() {
        return this.state == State.GARBAGE_COLLECTED;
    }


    /**
     * 标记为已回收
     */
    public void markAsCollected() {
        this.state = State.GARBAGE_COLLECTED;
    }

    /**
     * 获取对象的摘要信息
     */
    public String getSummary() {
        return String.format("TrackedReference[id=%s, class=%s, state=%s, age=%s, idle=%s, metadata=%s]",
                id, className, state, getAge(), getIdleTime(), getMetadata().toString());
    }

    @Override
    public String toString() {
        return getSummary();
    }
}