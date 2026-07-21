package zxf.monitor.object;

import java.time.Instant;

/**
 * 统计数据类
 *
 * @author davis
 */
public record MonitorStats(String className, long activeCount, long totalCreated, long totalCollected,
                           long totalLeakSuspected, long totalLeakConfirmed, double avgObjectAgeSeconds,
                           Instant timestamp) {
    /**
     * 未回收率 = 当前活跃数 / 累计创建数
     *
     * @return 未回收率（0~1）
     */
    public double getUncollectedRate() {
        return totalCreated > 0 ? (double) activeCount / totalCreated : 0.0;
    }
}