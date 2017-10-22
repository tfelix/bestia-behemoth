package net.bestia.zoneserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main application entry point. This will start the spring-boot application.
 * 
 * @author Thomas Felix
 *
 */
@SpringBootApplication(scanBasePackages = { "net.bestia" })
@EnableJpaRepositories(basePackages = { "net.bestia.model.dao" })
@EntityScan(basePackages = { "net.bestia.model.domain" })
public class Application {

	/**
	 * Main entry point of the application.
	 */
	public static void main(String[] args) throws Exception {
		SpringApplication.run(Application.class, args);
	}
}