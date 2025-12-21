package zxf.perf.app.service;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;

@Component
public class HttpClientFactory {
    public CloseableHttpClient newHttpClient() {
        return HttpClients.createDefault();
    }
}
