package zxf.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@SpringBootApplication
public class MockApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockApplication.class, args);
    }

    @GetMapping("/text")
    public Resource text() {
        System.out.println(LocalDateTime.now() + " - text");
        return new ClassPathResource("163.txt");
    }

    @GetMapping("/binary")
    public Resource binary() {
        System.out.println(LocalDateTime.now() + " - binary");
        return new ClassPathResource("163.dat");
    }
}
