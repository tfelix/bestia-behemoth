package net.bestia.next.webserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.web.SpringBootServletInitializer;

@SpringBootApplication
public class WebserverApplication extends SpringBootServletInitializer implements CommandLineRunner {
	
	private final static Logger LOG = LoggerFactory.getLogger(WebserverApplication.class);
	
	@Autowired
	private String serverName;
	
	public static void main(String[] args) throws Exception {
		SpringApplication.run(WebserverApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		LOG.info("Starting Webserver: {}", serverName);
	}
}
