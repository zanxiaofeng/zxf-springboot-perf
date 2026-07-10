package zxf.perf.app.service;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import zxf.perf.app.http4.HttpClientMonitor;

@Component
public class WebClientFactory {
    @Autowired
    private HttpClientMonitor monitor;

    public CloseableHttpClient newHttpClient() {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        monitor.monitor(httpClient);
        return httpClient;
    }
}
