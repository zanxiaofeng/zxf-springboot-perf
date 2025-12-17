package zxf.util;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class JCmdInvoker {
    public static List<String> getClassHistogram() throws Exception {
        Process process = Runtime.getRuntime().exec(buildJCmdCommand() + " " + getCurrentPid() + " GC.class_histogram");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            List<String> result = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
            }
            process.waitFor();
            return result;
        }
    }

    private static String getCurrentPid() {
        return java.lang.management.ManagementFactory
                .getRuntimeMXBean().getName().split("@")[0];
    }

    private static String buildJCmdCommand() {
        String javaHome = System.getProperty("java.home");
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            return javaHome + "\\bin\\jcmd.exe";
        } else {
            return javaHome + "/bin/jcmd";
        }
    }
}
