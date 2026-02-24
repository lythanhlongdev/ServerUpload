package ltldev.SeverUpFile.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

@Component
public class ServerStartupLogger {

    @Value("${server.port}")
    private String port;

    public static String getLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (var addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress()
                            && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "localhost";
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logServerUrl() {
        String ip = getLocalIp();

        System.out.println("\n=================================");
        System.out.println("Server running at:");
        System.out.println("http://" + ip + ":" + port);
        System.out.println("=================================\n");
    }
}