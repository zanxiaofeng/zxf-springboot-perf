package zxf.perf.app.service;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zxf.perf.app.http5.HttpClientMonitor;

@Component
public class WebClientFactory {
    @Autowired
    private HttpClientMonitor monitor;

    public RestTemplate newRestTemplateWithDefaultHttpClient() throws Exception {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        monitor.monitor(requestFactory.getHttpClient());
        return restTemplate;
    }

    public RestTemplate newRestTemplateWithCustomHttpClientWithPool() throws Exception {
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setTimeToLive(TimeValue.ofMinutes(30))
                        .setConnectTimeout(Timeout.ofSeconds(10))
                        .setSocketTimeout(Timeout.ofSeconds(60))
                        .setValidateAfterInactivity(TimeValue.ofSeconds(60))
                        .build())
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoKeepAlive(true)
                        .setTcpNoDelay(true)
                        .setSoTimeout(Timeout.ofSeconds(60))
                        .build())
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(20)
                .build();

        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                        .setResponseTimeout(Timeout.ofSeconds(30))
                        .build())
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        monitor.monitor(httpClient);
        return restTemplate;
    }

    public CloseableHttpClient newHttpClient() throws Exception {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        monitor.monitor(httpClient);
        return httpClient;
    }
}
