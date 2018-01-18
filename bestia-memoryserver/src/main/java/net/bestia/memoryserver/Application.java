package net.bestia.memoryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import scala.util.control.Exception;

/**
 * Main application entry point. This will start the spring-boot application.
 * 
 * @author Thomas Felix
 *
 */
@SpringBootApplication
public class Application {

	/**
	 * Main entry point of the application.
	 */
	public static void main(String[] args) throws Exception {
		SpringApplication.run(Application.class, args);
	}
}
