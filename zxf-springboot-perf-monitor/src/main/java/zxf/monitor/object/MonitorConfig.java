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
    Duration checkInterval = Duration.ofSeconds(30);
    Duration tatsInterval = Duration.ofSeconds(120);
    boolean autoGcBeforeCheck = false;
    Duration maxObjectAge = Duration.ofHours(1);
    int leakSuspectThreshold = 1000;
}
