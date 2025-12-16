package zxf.perf.app.http5;

import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import zxf.perf.monitor.MemoryMonitorManager;
import zxf.perf.monitor.ObjectMonitor;
import zxf.perf.monitor.TrackedReference;

import java.time.Duration;

@Component
public class HttpClientMonitor {

    public HttpClientMonitor() {
        MemoryMonitorManager.getInstance().getMonitor(HttpComponentsClientHttpRequestFactory.class).addListener(
                new ObjectMonitor.MonitorListener<>() {
                    @Override
                    public void onObjectRegistered(TrackedReference<HttpComponentsClientHttpRequestFactory> ref) {
                    }

                    @Override
                    public void onObjectCollected(TrackedReference<HttpComponentsClientHttpRequestFactory> ref) {
                    }

                    @Override
                    public void onLeakSuspected(TrackedReference<HttpComponentsClientHttpRequestFactory> ref, String reason) {
                        System.err.println("⚠️ 连接泄漏嫌疑: " + ref.getSummary() + ", 原因: " + reason);
                    }

                    @Override
                    public void onLeakConfirmed(TrackedReference<HttpComponentsClientHttpRequestFactory> ref, String reason) {
                        System.err.println("❌ 确认连接泄漏: " + ref.getSummary() + ", 原因: " + reason);
                    }

                    @Override
                    public void onObjectExpired(TrackedReference<HttpComponentsClientHttpRequestFactory> ref) {
                        System.out.println("连接过期: " + ref.getSummary());
                    }

                    @Override
                    public void onStatsUpdated(ObjectMonitor.Stats stats) {
                        // 可以记录统计信息到日志
                    }
                });

        // 配置监控器
        MemoryMonitorManager.getInstance().getMonitor(HttpComponentsClientHttpRequestFactory.class).configure(config -> {
            config.setLeakSuspectThreshold(500);
            config.setLeakConfirmThreshold(100);
            config.setMaxObjectAge(Duration.ofMinutes(30));
            config.setCheckInterval(Duration.ofSeconds(180));
            config.setAutoGcBeforeCheck(true);
        });
    }

    public void monitor(HttpComponentsClientHttpRequestFactory factory) {
        MemoryMonitorManager.getInstance().monitorObject(factory, null);
    }
}