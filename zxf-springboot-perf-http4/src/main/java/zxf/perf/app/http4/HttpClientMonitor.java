package zxf.perf.app.http4;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.springframework.stereotype.Component;
import zxf.monitor.*;
import zxf.monitor.object.MonitorListener;
import zxf.monitor.object.ObjectMonitor;
import zxf.monitor.object.TReference;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class HttpClientMonitor {
    private final ObjectMonitor<Closeable> closeableMonitor;
    private final ThreadMonitor threadMonitor;
    private final ClassMonitor classMonitor;
    private final DescriptorMonitor descriptorMonitor;
    private final Set<Class<?>> closableClasses = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<Class<?>, Field> CLOSEABLES_FIELD_CACHE = new ConcurrentHashMap<>();

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
            public void onLeakSuspected(TReference<Closeable> ref, String reason) {
                log.warn("连接泄漏嫌疑: {}, 原因: {}", ref.getSummary(), reason);
            }

            @Override
            public void onLeakConfirmed(TReference<Closeable> ref, String reason) {
                log.error("确认连接泄漏: {}, 原因: {}", ref.getSummary(), reason);
            }
        });

        threadMonitor = new ThreadMonitor(Duration.ofSeconds(90), new String[]{"org.apache.http", "Connection evictor"}, 1000);
        threadMonitor.start();

        classMonitor = new ClassMonitor(Duration.ofSeconds(90),
                new String[]{
                        "org.apache.http",           // HttpClient 核心
                        "org.apache.http.impl.conn", // 连接管理
                        "org.apache.http.conn",      // 连接接口
                        "org.apache.http.pool",      // 连接池
                        "org.apache.http.impl.client", // 客户端实现
                        "java.net.Socket",           // Socket 连接
                        "java.net.SocksSocketImpl",  // SOCKS 代理
                        "javax.net.ssl",             // SSL/TLS
                        "sun.net.www",               // HTTP 协议处理器
                        "sun.nio.ch",                // NIO 通道
                        "java.lang.Thread"           // 线程
                }, 100);
        classMonitor.start();

        descriptorMonitor = new DescriptorMonitor(Duration.ofSeconds(90), 5000, true);
        descriptorMonitor.start();
    }

    public void monitor(HttpClient httpClient) {
        // 反射查找按类缓存，避免压测热路径上重复 getDeclaredField
        Field field = CLOSEABLES_FIELD_CACHE.computeIfAbsent(httpClient.getClass(), HttpClientMonitor::lookupCloseablesField);
        if (field == null) {
            log.warn("Cannot access closeables field on {}. HttpClient implementation may have changed.",
                    httpClient.getClass().getName());
            return;
        }
        try {
            Object value = field.get(httpClient);
            if (value == null) {
                log.warn("HttpClient closeables field is null, skipping monitoring");
                return;
            }
            if (!(value instanceof List)) {
                log.warn("Unexpected closeables field type: {}", value.getClass());
                return;
            }
            @SuppressWarnings("unchecked")
            List<Closeable> closeables = (List<Closeable>) value;
            for (Closeable closeable : closeables) {
                if (closableClasses.add(closeable.getClass())) {
                    log.info("closable class: {}", closeable.getClass());
                }
                closeableMonitor.register(closeable, null);
            }
        } catch (IllegalAccessException e) {
            log.warn("Cannot access HttpClient internals for monitoring.", e);
        }
    }

    private static Field lookupCloseablesField(Class<?> clientClass) {
        try {
            Field field = clientClass.getDeclaredField("closeables");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException e) {
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        closeableMonitor.shutdown();
        threadMonitor.stop();
        classMonitor.stop();
        descriptorMonitor.stop();
    }
}