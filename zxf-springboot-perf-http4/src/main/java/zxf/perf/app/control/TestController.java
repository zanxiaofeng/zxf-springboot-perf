package zxf.perf.app.control;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zxf.perf.app.service.HttpClientFactory;
import zxf.perf.app.service.RestTemplateFactory;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@RestController
public class TestController {
    @Autowired
    private RestTemplateFactory restTemplateFactory;
    @Autowired
    private HttpClientFactory httpClientFactory;

    @GetMapping("/template/new/default")
    public ResponseEntity<String> newRestTemplateDefault(@RequestParam(required = false) Integer delay) throws Exception {
        if (delay != null) {
            Thread.sleep(delay * 1000);
        }
        RestTemplate restTemplate = restTemplateFactory.newRestTemplateWithDefaultHttpClient();
        return ResponseEntity.ok(testRestTemplate(restTemplate, delay));
    }

    @GetMapping("/template/new/custom/pool")
    public ResponseEntity<String> newRestTemplateCustomPool(@RequestParam(required = false) Integer delay) throws Exception {
        if (delay != null) {
            Thread.sleep(delay * 1000);
        }
        RestTemplate restTemplate = restTemplateFactory.newRestTemplateWithCustomHttpClientWithPool();
        return ResponseEntity.ok(testRestTemplate(restTemplate, delay));
    }

    @GetMapping("/httpclient/new/default")
    public ResponseEntity<String> newHttpClientDefault(@RequestParam(required = false) Integer delay, @RequestParam(defaultValue = "true") Boolean close) throws Exception {
        if (delay != null) {
            Thread.sleep(delay * 1000);
        }
        CloseableHttpClient httpClient = httpClientFactory.newHttpClient();
        return ResponseEntity.ok(testHttpClient(httpClient, delay, close));
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
            return response.getStatusLine().toString();
        } finally {
            if (close) {
                httpClient.close();
            }
        }
    }
}
