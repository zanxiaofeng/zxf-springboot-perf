package zxf.monitor;

import lombok.Data;

import java.time.Duration;

/**
 * 配置参数
 */
@Data
public class MonitorConfig {
    Duration checkInterval = Duration.ofSeconds(30); // 检查间隔
    boolean autoGcBeforeCheck = false; // 检查前自动GC
    int leakSuspectThreshold = 1000; // 泄漏嫌疑阈值
    Duration maxObjectAge = Duration.ofHours(1); // 最大对象年龄
}
