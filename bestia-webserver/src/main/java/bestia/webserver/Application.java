package bestia.webserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;

/**
 * Main entry point of the webserver application.
 * 
 * @author Thomas Felix
 *
 */
@SpringBootApplication
public class Application extends SpringBootServletInitializer {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(Application.class, args);
	}

}
