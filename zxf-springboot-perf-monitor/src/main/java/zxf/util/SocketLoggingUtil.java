package zxf.util;

import java.util.logging.*;

public class SocketLoggingUtil {

    public static void enableAllNetworkLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.FINEST);

        Logger.getLogger("sun.net").setLevel(Level.ALL);
        Logger.getLogger("sun.nio.ch").setLevel(Level.ALL);
        Logger.getLogger("java.net").setLevel(Level.ALL);
        Logger.getLogger("javax.net").setLevel(Level.ALL);
        Logger.getLogger("javax.net.ssl").setLevel(Level.ALL);
        Logger.getLogger("com.sun.net.ssl").setLevel(Level.ALL);
        Logger.getLogger("sun.rmi").setLevel(Level.ALL);
        Logger.getLogger("sun.rmi.transport").setLevel(Level.ALL);
        Logger.getLogger("sun.rmi.transport.tcp").setLevel(Level.ALL);
    }

    public static void enableSocketDebug() {
        // 启用Socket调试
        System.setProperty("javax.net.debug", "all");
        System.setProperty("sun.net.www.level", "all");
        System.setProperty("sun.net.www.http.level", "all");
        System.setProperty("sun.net.www.http.HttpClient.level", "all");
        System.setProperty("sun.net.www.protocol.http.HttpURLConnection.level", "all");
        System.setProperty("sun.net.www.protocol.https.HttpsURLConnection.level", "all");

        // 启用TCP Keep-Alive调试
        System.setProperty("sun.net.www.http.HttpClient.logging", "true");

        // 启用连接池调试
        System.setProperty("sun.net.http.nodelay", "true");
        System.setProperty("sun.net.client.defaultConnectTimeout", "5000");
        System.setProperty("sun.net.client.defaultReadTimeout", "5000");
    }
}