package zxf.perf.app.http4;

import org.apache.http.client.HttpClient;
import org.springframework.stereotype.Component;
import zxf.monitor.*;
import zxf.monitor.object.MonitorListener;
import zxf.monitor.object.MonitorStats;
import zxf.monitor.object.ObjectMonitor;
import zxf.monitor.object.TReference;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;

@Component
public class HttpClientMonitor {
    private final ObjectMonitor<Closeable> closeableMonitor;
    private final ThreadMonitor threadMonitor;
    private final ClassMonitor classMonitor;
    private final DescriptorMonitor descriptorMonitor;

    public HttpClientMonitor() {
        closeableMonitor = new ObjectMonitor<>(Closeable.class);

        closeableMonitor.startup(config -> {
            config.setCheckInterval(Duration.ofSeconds(30));
            config.setTatsInterval(Duration.ofSeconds(60));
            config.setAutoGcBeforeCheck(true);
            config.setLeakSuspectThreshold(5000);
            config.setMaxObjectAge(Duration.ofMinutes(10));
        }, new MonitorListener<Closeable>() {
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

        threadMonitor = new ThreadMonitor(Duration.ofSeconds(90), new String[]{"org.apache.http", "Connection evictor"}, 1000);
        threadMonitor.start();

        classMonitor = new ClassMonitor(Duration.ofSeconds(90), new String[]{"org.apache.http","javax.net.ssl", "java.lang.Thread"}, 5000);
        classMonitor.start();

        descriptorMonitor = new DescriptorMonitor(Duration.ofSeconds(90), 5000);
        descriptorMonitor.start();
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