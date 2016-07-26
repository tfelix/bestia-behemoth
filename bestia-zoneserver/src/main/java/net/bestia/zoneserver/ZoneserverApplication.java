package net.bestia.zoneserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.orm.jpa.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories("net.bestia.model")
@EntityScan("net.bestia.model.domain")
public class ZoneserverApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(ZoneserverApplication.class, args);
	}
}
