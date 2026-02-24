package ltldev.SeverUpFile.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.AbstractProtocol;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TomcatStartupLog {

    private final WebServerApplicationContext context;

    public TomcatStartupLog(WebServerApplicationContext context) {
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logTomcatInfo() {

        var webServer = context.getWebServer();

        if (!webServer.getClass().getName().contains("Tomcat")) {
            log.info("Server is not Tomcat");
            return;
        }

        try {
            var tomcatField = webServer.getClass().getDeclaredField("tomcat");
            tomcatField.setAccessible(true);
            var tomcat = tomcatField.get(webServer);

            var service = tomcat.getClass().getMethod("getService").invoke(tomcat);
            Connector[] connectors = (Connector[]) service.getClass()
                    .getMethod("findConnectors")
                    .invoke(service);

            for (Connector connector : connectors) {

                var handler = (AbstractProtocol<?>) connector.getProtocolHandler();

                log.info("========== TOMCAT STARTUP INFO ==========");
                log.info("Port: {}", connector.getPort());
                log.info("Protocol: {}", connector.getProtocol());
                log.info("Scheme: {}", connector.getScheme());

                log.info("Max Threads: {}", handler.getMaxThreads());
                log.info("Min Spare Threads: {}", handler.getMinSpareThreads());
                log.info("Max Connections: {}", handler.getMaxConnections());
                log.info("Accept Count: {}", handler.getAcceptCount());

                log.info("Connection Timeout: {}", handler.getConnectionTimeout());
                log.info("KeepAlive Timeout: {}", handler.getKeepAliveTimeout());

                log.info("Max Post Size: {}", connector.getMaxPostSize());

                log.info("==========================================");
            }

        } catch (Exception e) {
            log.error("Cannot read Tomcat config", e);
        }
    }
}