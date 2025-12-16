package zxf.perf.app.control;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zxf.perf.app.service.RestTemplateFactory;
import zxf.perf.app.service.RestTemplateTestService;

@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {
    @Autowired
    private RestTemplateFactory restTemplateFactory;
    @Autowired
    private RestTemplateTestService restTemplateTestService;

    @GetMapping("/new")
    public ResponseEntity<String> okTest() {
        RestTemplate restTemplate = restTemplateFactory.createNewRestTemplate();
        return ResponseEntity.ok(restTemplateTestService.test(restTemplate));
    }
}
