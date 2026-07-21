package zxf.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new RuntimeException("jcmd command timed out after 30 seconds");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new RuntimeException("jcmd command failed with exit code: " + exitCode);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for jcmd", e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String getCurrentPid() {
        return String.valueOf(ProcessHandle.current().pid());
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
