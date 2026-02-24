package ltldev.SeverUpFile;

import ltldev.SeverUpFile.logging.AppLogger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SeverUpFileApplication {

	public static void main(String[] args) {
		AppLogger.info("Active threads before start  SpringApplication  " + Thread.activeCount());
		SpringApplication.run(SeverUpFileApplication.class, args);
		AppLogger.info("Active threads after start  SpringApplication  " + Thread.activeCount());
	}

}
