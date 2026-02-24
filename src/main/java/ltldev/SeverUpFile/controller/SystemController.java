package ltldev.SeverUpFile.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import ltldev.SeverUpFile.logging.AppLogger;
import ltldev.SeverUpFile.service.UploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

@Data
@RestController
//@RequestMapping("${api.prefix}/system")
@RequestMapping("/system")
public class SystemController {

    @Value("${server.port}")
    private String port;

    @GetMapping("/server-url")
    public String getServerUrl() {
        String ip = getLocalIp();
        return "http://" + ip + ":" + port;
    }

    private String getLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {

                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {

                    if (!addr.isLoopbackAddress()
                            && addr instanceof Inet4Address) {

                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "127.0.0.1";
    }
}
