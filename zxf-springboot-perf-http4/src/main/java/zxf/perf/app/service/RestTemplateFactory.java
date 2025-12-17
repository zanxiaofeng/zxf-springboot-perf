package zxf.perf.app.service;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zxf.perf.app.http4.HttpClientMonitor;

import java.util.concurrent.TimeUnit;

@Component
public class RestTemplateFactory {
    @Autowired
    private HttpClientMonitor monitor;

    public RestTemplate createNewRestTemplate() {
        HttpClient httpClient = HttpClients.custom().setConnectionManager(new PoolingHttpClientConnectionManager())
                .evictIdleConnections(30, TimeUnit.SECONDS)
                .build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate restTemplate = new RestTemplate(requestFactory);

        monitor.monitor(httpClient);
        return restTemplate;
    }
}
