package zxf.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class MockController {
    private static final int MAX_DELAY_SECONDS = 30;

    @GetMapping("/text")
    public Object text(@RequestParam(required = false) Integer delay) throws InterruptedException {
        if (delay != null) {
            if (delay < 0 || delay > MAX_DELAY_SECONDS) {
                return ResponseEntity.badRequest()
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
                return ResponseEntity.badRequest()
                        .body("delay must be between 0 and " + MAX_DELAY_SECONDS);
            }
            Thread.sleep(delay * 1000L);
        }

        log.info("binary");
        return new ClassPathResource("163.dat");
    }
}
