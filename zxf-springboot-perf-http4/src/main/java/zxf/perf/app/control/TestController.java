package zxf.perf.app.control;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zxf.perf.app.service.WebClientFactory;

import java.io.IOException;

@Slf4j
@RestController
public class TestController {
    @Autowired
    private WebClientFactory webClientFactory;

    private static final int MAX_DELAY_SECONDS = 30;

    // NOTE: HttpClient is intentionally created per request and may be left unclosed,
    // to demonstrate and observe resource leak behavior under load testing.
    // Spring Framework 6+ 的 HttpComponentsClientHttpRequestFactory 仅支持 HttpClient 5，
    // 无法再用 RestTemplate 接入 HttpClient 4，故本模块仅保留裸 HttpClient 4 端点。

    @GetMapping("/httpclient/new/default")
    public ResponseEntity<String> newHttpClientDefault(@RequestParam(required = false) Integer delay, @RequestParam(defaultValue = "true") Boolean close) throws Exception {
        if (delay != null) {
            if (delay < 0 || delay > MAX_DELAY_SECONDS) {
                return ResponseEntity.badRequest().body("delay must be between 0 and " + MAX_DELAY_SECONDS);
            }
            Thread.sleep(delay * 1000L);
        }
        return ResponseEntity.ok(testHttpClient(webClientFactory.newHttpClient(), delay, close));
    }

    private String testHttpClient(CloseableHttpClient httpClient, Integer delay, Boolean close) throws IOException {
        try {
            String requestUrl = delay != null ? "http://localhost:8089/binary?delay=" + delay : "http://localhost:8089/binary";
            CloseableHttpResponse response = httpClient.execute(new HttpGet(requestUrl));
            String result = response.getStatusLine().toString();
            if (close) {
                EntityUtils.consume(response.getEntity());
                response.close();
            }
            return result;
        } finally {
            if (close) {
                httpClient.close();
            }
        }
    }
}
