package ltldev.SeverUpFile.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AppLogger {

    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void info(String message) {
        System.out.println("[INFO]  " + now() + "  " + message);
    }

    public static void warn(String message) {
        System.out.println("[WARN]  " + now() + "  " + message);
    }

    public static void error(String message) {
        System.out.println("[ERROR] " + now() + "  " + message);
    }

    private static String now() {
        return LocalDateTime.now().format(formatter);
    }
}