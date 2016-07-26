package de.bestia.next.zoneserver;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ZoneserverApplication implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(ZoneserverApplication.class);


	public static void main(String[] args) throws Exception {
		SpringApplication.run(ZoneserverApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		

		try {
			System.in.read();
		} catch (

		IOException e) {

		}
		LOG.info("Shutting down.");
	}
}
