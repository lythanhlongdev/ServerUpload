package ltldev.SeverUpFile.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import ltldev.SeverUpFile.service.MonitorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final MonitorService monitorService;

    @Value("${server.port}")
    private String port;

    @GetMapping("/server-url")
    public String getServerUrl() {
        String ip = getLocalIp();
        return "http://" + ip + ":" + port;
    }


    @GetMapping("")
    public Map<String, Object> monitor() {
        return monitorService.getSystemMonitor();
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
