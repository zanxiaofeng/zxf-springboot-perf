package zxf.perf.app.service;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zxf.perf.app.http5.HttpClientMonitor;

@Component
public class RestTemplateFactory {
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
                        .setTimeToLive(TimeValue.ofMinutes(1)).build())
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(20)
                .build();

        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        monitor.monitor(httpClient);
        return restTemplate;
    }
}
