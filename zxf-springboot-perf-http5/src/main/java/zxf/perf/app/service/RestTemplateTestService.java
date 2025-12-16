package zxf.perf.app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class RestTemplateTestService {
    public String test(RestTemplate restTemplate) {
        return restTemplate.getForObject("http://localhost:8089/", String.class);
    }
}
