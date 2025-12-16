package zxf.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class MockApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockApplication.class, args);
    }

    @GetMapping("/")
    public String index() {
        System.out.println("index");
        return "Hello World";
    }
}
