package zxf.perf.app.service;


import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.stereotype.Component;

@Component
public class HttpClientFactory {
    public CloseableHttpClient newHttpClient() {
        return HttpClients.createDefault();
    }
}
