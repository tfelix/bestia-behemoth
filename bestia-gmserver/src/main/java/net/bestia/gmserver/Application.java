package net.bestia.gmserver;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {
	
	private Logger LOG = LogManager.getLogger(getClass());
	
	@Bean
	RestTemplate restTemplate() {
		return new RestTemplate();
	}
	
	@Bean
	CommandLineRunner init() {
		return args -> {
			LOG.info("App GM-Server has started.");
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
