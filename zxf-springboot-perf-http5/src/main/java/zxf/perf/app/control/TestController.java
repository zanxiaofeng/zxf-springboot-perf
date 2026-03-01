package zxf.perf.app.control;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zxf.perf.app.service.WebClientFactory;

import java.io.IOException;

@Slf4j
@RestController
public class TestController {
    @Autowired
    private WebClientFactory webClientFactory;

    private static final int MAX_DELAY_SECONDS = 30;

    @GetMapping("/template/new/default")
    public ResponseEntity<String> newRestTemplateDefault(@RequestParam(required = false) Integer delay) throws Exception {
        if (delay != null) {
            if (delay < 0 || delay > MAX_DELAY_SECONDS) {
                return ResponseEntity.badRequest().body("delay must be between 0 and " + MAX_DELAY_SECONDS);
            }
            Thread.sleep(delay * 1000L);
        }
        return ResponseEntity.ok(testRestTemplate(webClientFactory.newRestTemplateWithDefaultHttpClient(), delay));
    }

    @GetMapping("/template/new/custom/pool")
    public ResponseEntity<String> newRestTemplateCustomPool(@RequestParam(required = false) Integer delay) throws Exception {
        if (delay != null) {
            if (delay < 0 || delay > MAX_DELAY_SECONDS) {
                return ResponseEntity.badRequest().body("delay must be between 0 and " + MAX_DELAY_SECONDS);
            }
            Thread.sleep(delay * 1000L);
        }
        return ResponseEntity.ok(testRestTemplate(webClientFactory.newRestTemplateWithCustomHttpClientWithPool(), delay));
    }

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


    private String testRestTemplate(RestTemplate restTemplate, Integer delay) {
        if (delay != null) {
            return restTemplate.getForObject("http://localhost:8089/binary?delay={delay}", String.class, delay.toString());
        }
        return restTemplate.getForObject("http://localhost:8089/binary", String.class);
    }

    private String testHttpClient(CloseableHttpClient httpClient, Integer delay, Boolean close) throws IOException {
        try {
            String requestUrl = delay != null ? "http://localhost:8089/binary?delay=" + delay : "http://localhost:8089/binary";
            CloseableHttpResponse response = httpClient.execute(new HttpGet(requestUrl));
            if (close) {
                EntityUtils.consume(response.getEntity());
                response.close();
            }
            return String.valueOf(response.getCode());
        } finally {
            if (close) {
                httpClient.close();
            }
        }
    }
}
