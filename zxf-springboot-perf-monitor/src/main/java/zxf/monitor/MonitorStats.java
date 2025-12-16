package zxf.monitor;

import java.time.Instant;

/**
 * 统计数据类
 *
 * @author davis
 */
public record MonitorStats(String className, long activeCount, long totalCreated, long totalCollected,
                           long totalLeakSuspected, long totalLeakConfirmed, double avgObjectAgeSeconds,
                           Instant timestamp) {
    public double getLeakRate() {
        return totalCreated > 0 ? (double) activeCount / totalCreated : 0.0;
    }
}