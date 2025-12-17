package zxf.perf.app.http5;

import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.stereotype.Component;
import zxf.monitor.*;

import java.time.Duration;

@Component
public class HttpClientMonitor {
    private final ObjectMonitor<HttpClient> monitorManager;
    private final ThreadMonitor threadMonitor;

    public HttpClientMonitor() {
        monitorManager = new ObjectMonitor<>(HttpClient.class);

        monitorManager.startup(config -> {
            config.setCheckInterval(Duration.ofSeconds(30));
            config.setTatsInterval(Duration.ofSeconds(60));
            config.setAutoGcBeforeCheck(true);
            config.setLeakSuspectThreshold(5000);
            config.setMaxObjectAge(Duration.ofMinutes(10));
        }, new MonitorListener<>() {
            @Override
            public void onObjectRegistered(TReference<HttpClient> ref) {
                //System.out.println("注册连接: " + ref.getSummary());
            }

            @Override
            public void onObjectCollected(TReference<HttpClient> ref) {
                //System.out.println("收集连接: " + ref.getSummary());
            }

            @Override
            public void onLeakSuspected(TReference<HttpClient> ref, String reason) {
                System.err.println("⚠️ 连接泄漏嫌疑: " + ref.getSummary() + ", 原因: " + reason);
            }

            @Override
            public void onLeakConfirmed(TReference<HttpClient> ref, String reason) {
                System.err.println("❌ 确认连接泄漏: " + ref.getSummary() + ", 原因: " + reason);
            }

            @Override
            public void onStatsUpdated(MonitorStats stats) {
                // 可以记录统计信息到日志
            }
        });

        threadMonitor = new ThreadMonitor(new String[]{"org.apache.hc.client5", "idle-connection-evictor"}, true);
        threadMonitor.start();
    }

    public void monitor(HttpClient factory) {
        monitorManager.register(factory, null);
    }
}