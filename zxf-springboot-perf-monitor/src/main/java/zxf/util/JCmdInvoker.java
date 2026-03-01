package zxf.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class JCmdInvoker {
    public static List<String> getClassHistogram() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(buildJCmdCommand(), getCurrentPid(), "GC.class_histogram");
        pb.redirectErrorStream(true);
        Process process = null;
        try {
            process = pb.start();
            List<String> result = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.add(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("jcmd command failed with exit code: " + exitCode);
            }
            return result;
        } finally {
            if (process != null) {
                process.destroy();
            }
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
