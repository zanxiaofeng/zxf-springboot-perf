package zxf.perf.app.http4;

import org.apache.http.client.HttpClient;
import org.springframework.stereotype.Component;
import zxf.monitor.MonitorListener;
import zxf.monitor.MonitorStats;
import zxf.monitor.ObjectMonitor;
import zxf.monitor.TReference;

import java.time.Duration;

@Component
public class HttpClientMonitor {
    private final ObjectMonitor<HttpClient> monitorManager;

    public HttpClientMonitor() {
        monitorManager = new ObjectMonitor<>(HttpClient.class);

        monitorManager.startup(config -> {
            config.setCheckInterval(Duration.ofSeconds(30));
            config.setTatsInterval(Duration.ofSeconds(60));
            config.setAutoGcBeforeCheck(true);
            config.setLeakSuspectThreshold(5000);
            config.setMaxObjectAge(Duration.ofMinutes(10));
        }, new MonitorListener<HttpClient>() {
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

            }
        });
    }

    public void monitor(HttpClient factory) {
        monitorManager.register(factory, null);
    }
}