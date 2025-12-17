package zxf.perf.app.control;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zxf.perf.app.service.RestTemplateFactory;

@Slf4j
@RestController
@RequestMapping("/httpclient")
public class TestController {
    @Autowired
    private RestTemplateFactory restTemplateFactory;

    @GetMapping("/new/default")
    public ResponseEntity<String> newDefault() throws Exception {
        RestTemplate restTemplate = restTemplateFactory.newRestTemplateWithDefaultHttpClient();
        return ResponseEntity.ok(testRestTemplate(restTemplate));
    }

    @GetMapping("/new/custom/pool")
    public ResponseEntity<String> okTest() throws Exception {
        RestTemplate restTemplate = restTemplateFactory.newRestTemplateWithCustomHttpClientWithPool();
        return ResponseEntity.ok(testRestTemplate(restTemplate));
    }

    private String testRestTemplate(RestTemplate restTemplate) {
        return restTemplate.getForObject("http://localhost:8089/binary", String.class);
    }
}
