package zxf.perf.app.http5;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.springframework.stereotype.Component;
import zxf.monitor.*;
import zxf.monitor.object.MonitorListener;
import zxf.monitor.object.MonitorStats;
import zxf.monitor.object.ObjectMonitor;
import zxf.monitor.object.TReference;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class HttpClientMonitor {
    private final ObjectMonitor<Closeable> closeableMonitor;
    private final ThreadMonitor threadMonitor;
    private final ClassMonitor classMonitor;
    private final DescriptorMonitor descriptorMonitor;
    private final Set<Class<?>> closableClasses = ConcurrentHashMap.newKeySet();

    public HttpClientMonitor() {
        closeableMonitor = new ObjectMonitor<>(Closeable.class);

        closeableMonitor.startup(config -> {
            config.setCheckInterval(Duration.ofSeconds(30));
            config.setStatsInterval(Duration.ofSeconds(60));
            config.setAutoGcBeforeCheck(true);
            config.setLeakSuspectThreshold(5000);
            config.setMaxObjectAge(Duration.ofMinutes(10));
        }, new MonitorListener<Closeable>() {
            @Override
            public void onObjectRegistered(TReference<Closeable> ref) {
                log.debug("注册连接: {}", ref.getSummary());
            }

            @Override
            public void onObjectCollected(TReference<Closeable> ref) {
                log.debug("收集连接: {}", ref.getSummary());
            }

            @Override
            public void onLeakSuspected(TReference<Closeable> ref, String reason) {
                log.warn("连接泄漏嫌疑: {}, 原因: {}", ref.getSummary(), reason);
            }

            @Override
            public void onLeakConfirmed(TReference<Closeable> ref, String reason) {
                log.error("确认连接泄漏: {}, 原因: {}", ref.getSummary(), reason);
            }

            @Override
            public void onStatsUpdated(MonitorStats stats) {
                // 可以记录统计信息到日志
            }
        });

        threadMonitor = new ThreadMonitor(Duration.ofSeconds(90), new String[]{"org.apache.hc.client5", "idle-connection-evictor"}, 1000);
        threadMonitor.start();

        classMonitor = new ClassMonitor(Duration.ofSeconds(5), new String[]{"org.apache.hc.client5","java.net.Socket", "javax.net", "sun.net", "sun.nio.ch.NioSocketImpl", "java.lang.Thread"}, 0);
        classMonitor.start();

        descriptorMonitor = new DescriptorMonitor(Duration.ofSeconds(90), 5000);
        descriptorMonitor.start();
    }

    public void monitor(HttpClient httpClient) {
        try {
            Field field = httpClient.getClass().getDeclaredField("closeables");
            field.setAccessible(true);
            Object value = field.get(httpClient);
            if (!(value instanceof Queue)) {
                log.warn("Unexpected closeables field type: {}", value.getClass());
                return;
            }
            @SuppressWarnings("unchecked")
            Queue<Closeable> closeables = (Queue<Closeable>) value;
            for (Closeable closeable : closeables) {
                if (closableClasses.add(closeable.getClass())) {
                    log.info("closable class: {}", closeable.getClass());
                }
                closeableMonitor.register(closeable, null);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.warn("Cannot access HttpClient internals for monitoring. "
                    + "HttpClient implementation may have changed.", e);
        }
    }
}