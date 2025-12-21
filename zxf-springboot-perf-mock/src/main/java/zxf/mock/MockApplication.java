package zxf.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@SpringBootApplication
public class MockApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockApplication.class, args);
    }

    @GetMapping("/text")
    public Resource text(@RequestParam(required = false) Integer delay) throws InterruptedException {
        if (delay != null) {
            Thread.sleep(delay * 1000);
        }

        System.out.println(LocalDateTime.now() + " - text");
        return new ClassPathResource("163.txt");
    }

    @GetMapping("/binary")
    public Resource binary(@RequestParam(required = false) Integer delay) throws InterruptedException {
        if (delay != null) {
            Thread.sleep(delay * 1000);
        }

        System.out.println(LocalDateTime.now() + " - binary");
        return new ClassPathResource("163.dat");
    }
}
