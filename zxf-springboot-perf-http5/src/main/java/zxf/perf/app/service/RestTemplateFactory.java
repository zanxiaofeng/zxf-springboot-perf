package zxf.perf.app.service;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zxf.perf.app.http5.HttpClientMonitor;

import java.util.concurrent.TimeUnit;

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
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(new PoolingHttpClientConnectionManager())
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        monitor.monitor(httpClient);
        return restTemplate;
    }
}
