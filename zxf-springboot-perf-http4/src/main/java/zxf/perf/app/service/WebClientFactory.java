package zxf.perf.app.service;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zxf.perf.app.http4.HttpClientMonitor;

import java.util.concurrent.TimeUnit;

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
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(new PoolingHttpClientConnectionManager())
                .setConnectionTimeToLive(30, TimeUnit.MINUTES)
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoKeepAlive(true)
                        .setTcpNoDelay(true)
                        .setSoTimeout(60000)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(10000)
                        .setConnectTimeout(10000)
                        .setSocketTimeout(60000)
                        .build())
                .evictIdleConnections(30, TimeUnit.MINUTES)
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
