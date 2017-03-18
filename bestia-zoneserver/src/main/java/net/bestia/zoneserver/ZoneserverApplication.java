package net.bestia.zoneserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main application entry point. This will start the spring-boot application.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@SpringBootApplication
@EnableJpaRepositories("net.bestia.model.dao")
@EntityScan("net.bestia.model.domain")
@ComponentScan(basePackages = { "net.bestia" })
public class ZoneserverApplication {

	/**
	 * Main entry point of the application.
	 */
	public static void main(String[] args) throws Exception {
		SpringApplication.run(ZoneserverApplication.class, args);
	}
}
