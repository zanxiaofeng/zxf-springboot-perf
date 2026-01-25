package zxf.perf.app.http4;

import org.apache.http.client.HttpClient;
import org.springframework.stereotype.Component;
import zxf.monitor.*;
import zxf.monitor.object.MonitorListener;
import zxf.monitor.object.MonitorStats;
import zxf.monitor.object.ObjectMonitor;
import zxf.monitor.object.TReference;

import javax.sound.sampled.BooleanControl;
import java.io.Closeable;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class HttpClientMonitor {
    private final ObjectMonitor<Closeable> closeableMonitor;
    private final ThreadMonitor threadMonitor;
    private final ClassMonitor classMonitor;
    private final DescriptorMonitor descriptorMonitor;
    private final Map<Class, Boolean> closableClasses = new ConcurrentHashMap<>();

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
                }, 0);
        classMonitor.start();

        descriptorMonitor = new DescriptorMonitor(Duration.ofSeconds(90), 5000, true);
        descriptorMonitor.start();
    }

    public void monitor(HttpClient httpClient) throws Exception {
        Field field = httpClient.getClass().getDeclaredField("closeables");
        field.setAccessible(true);
        List<Closeable> closeables = (List<Closeable>) field.get(httpClient);
        for (Closeable closeable : closeables) {
            if (!closableClasses.containsKey(closeable.getClass())) {
                closableClasses.putIfAbsent(closeable.getClass(), true);
                System.out.println("closable class： " + closeable.getClass());
            }
            closeableMonitor.register(closeable, null);
        }
    }
}