package zxf.monitor.object;

import lombok.Data;

import java.time.Duration;

/**
 * 配置参数
 *
 * @author davis
 */
@Data
public class MonitorConfig {
    private Duration checkInterval = Duration.ofSeconds(30);
    private Duration statsInterval = Duration.ofSeconds(60);
    private boolean autoGcBeforeCheck = false;
    private Duration maxObjectAge = Duration.ofHours(1);
    private int leakSuspectThreshold = 1000;
}
