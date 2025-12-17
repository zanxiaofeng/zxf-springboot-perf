package zxf.perf.app.http5;

import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.stereotype.Component;
import zxf.monitor.*;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;

@Component
public class HttpClientMonitor {
    private final ObjectMonitor<Closeable> closeableMonitor;
    private final ThreadMonitor threadMonitor;
    private final ClassMonitor classMonitor;

    public HttpClientMonitor() {
        closeableMonitor = new ObjectMonitor<>(Closeable.class);

        closeableMonitor.startup(config -> {
            config.setCheckInterval(Duration.ofSeconds(30));
            config.setTatsInterval(Duration.ofSeconds(60));
            config.setAutoGcBeforeCheck(true);
            config.setLeakSuspectThreshold(5000);
            config.setMaxObjectAge(Duration.ofMinutes(10));
        }, new MonitorListener<>() {
            @Override
            public void onObjectRegistered(TReference<Closeable> ref) {
                //System.out.println("注册连接: " + ref.getSummary());
            }

            @Override
            public void onObjectCollected(TReference<Closeable> ref) {
                //System.out.println("收集连接: " + ref.getSummary());
            }

            @Override
            public void onLeakSuspected(TReference<Closeable> ref, String reason) {
                System.err.println("⚠️ 连接泄漏嫌疑: " + ref.getSummary() + ", 原因: " + reason);
            }

            @Override
            public void onLeakConfirmed(TReference<Closeable> ref, String reason) {
                System.err.println("❌ 确认连接泄漏: " + ref.getSummary() + ", 原因: " + reason);
            }

            @Override
            public void onStatsUpdated(MonitorStats stats) {
                // 可以记录统计信息到日志
            }
        });

        threadMonitor = new ThreadMonitor(Duration.ofSeconds(10), new String[]{"org.apache.hc.client5", "idle-connection-evictor"}, 1);
        threadMonitor.start();

        classMonitor = new ClassMonitor(Duration.ofSeconds(10), new String[]{"org.apache.hc.client5"}, 1000);
        classMonitor.start();
    }

    public void monitor(HttpClient httpClient) throws Exception {
        Field field = httpClient.getClass().getDeclaredField("closeables");
        field.setAccessible(true);
        List<Closeable> closeables = (List<Closeable>) field.get(httpClient);
        for (Closeable closeable : closeables) {
            closeableMonitor.register(closeable, null);
        }
    }
}