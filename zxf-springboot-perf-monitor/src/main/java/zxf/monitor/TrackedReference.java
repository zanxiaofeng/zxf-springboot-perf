package zxf.monitor;


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
public class TrackedReference<T> extends WeakReference<T> {
    @Getter
    private final String id;
    @Getter
    private final String className;
    @Getter
    private final Instant creationTime;
    @Getter
    private final Instant registrationTime;
    @Getter
    private final String threadName;
    @Getter
    private final List<StackTraceElement> creationStackTrace;
    @Getter
    private final Map<String, Object> metadata;

    @Getter
    private volatile State state;
    @Getter
    private volatile Instant lastAccessTime;
    @Getter
    private volatile String lifecyclePhase;

    public TrackedReference(T referent, ReferenceQueue<? super T> queue, Map<String, Object> additionalMetadata) {
        super(referent, queue);
        this.id = UUID.randomUUID().toString();
        this.className = referent.getClass().getName();
        this.creationTime = Instant.now();
        this.registrationTime = Instant.now();
        this.threadName = Thread.currentThread().getName();
        this.creationStackTrace = List.of(Thread.currentThread().getStackTrace());
        this.metadata = new HashMap<>();

        this.state = State.ACTIVE;
        this.lastAccessTime = Instant.now();
        this.lifecyclePhase = "created";

        metadata.put("className", referent.getClass().getName());
        metadata.put("hashCode", System.identityHashCode(referent));
        if (additionalMetadata != null) {
            metadata.putAll(additionalMetadata);
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
        GARBAGE_COLLECTED,
        /**
         * 已过期（超时）
         */
        EXPIRED
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
     * 标记为疑似泄漏
     */
    public void markAsLeakSuspected(String reason) {
        this.state = State.LEAK_SUSPECTED;
        metadata.put("leakSuspectedReason", reason);
        metadata.put("leakSuspectedTime", Instant.now().toString());
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
     * 标记为过期
     */
    public void markAsExpired() {
        this.state = State.EXPIRED;
        metadata.put("expiredTime", Instant.now().toString());
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
        return String.format("TrackedReference[id=%s, class=%s, state=%s, age=%s, idle=%s]",
                id, className, state, getAge(), getIdleTime());
    }

    @Override
    public String toString() {
        return getSummary();
    }
}