package zxf.perf.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zxf.perf.app.http4.HttpClientMonitor;

@Component
public class RestTemplateFactory {
    @Autowired
    private HttpClientMonitor monitor;

    public RestTemplate createNewRestTemplate() {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        RestTemplate restTemplate = new RestTemplate(requestFactory);

        monitor.monitor(requestFactory.getHttpClient());
        return restTemplate;
    }
}
