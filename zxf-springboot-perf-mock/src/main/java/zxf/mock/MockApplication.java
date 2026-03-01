package zxf.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@SpringBootApplication
public class MockApplication {
    private static final int MAX_DELAY_SECONDS = 30;

    public static void main(String[] args) {
        SpringApplication.run(MockApplication.class, args);
    }

    @GetMapping("/text")
    public Object text(@RequestParam(required = false) Integer delay) throws InterruptedException {
        if (delay != null) {
            if (delay < 0 || delay > MAX_DELAY_SECONDS) {
                return org.springframework.http.ResponseEntity.badRequest()
                        .body("delay must be between 0 and " + MAX_DELAY_SECONDS);
            }
            Thread.sleep(delay * 1000L);
        }

        log.info("text");
        return new ClassPathResource("163.txt");
    }

    @GetMapping("/binary")
    public Object binary(@RequestParam(required = false) Integer delay) throws InterruptedException {
        if (delay != null) {
            if (delay < 0 || delay > MAX_DELAY_SECONDS) {
                return org.springframework.http.ResponseEntity.badRequest()
                        .body("delay must be between 0 and " + MAX_DELAY_SECONDS);
            }
            Thread.sleep(delay * 1000L);
        }

        log.info("binary");
        return new ClassPathResource("163.dat");
    }
}
