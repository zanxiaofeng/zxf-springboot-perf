package zxf.perf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import zxf.util.SocketLoggingUtil;

@SpringBootApplication
public class PerfApplication {

    public static void main(String[] args) {
        //SocketLoggingUtil.enableAllNetworkLogging();
        //SocketLoggingUtil.enableSocketDebug();
        SpringApplication.run(PerfApplication.class, args);
    }
}
