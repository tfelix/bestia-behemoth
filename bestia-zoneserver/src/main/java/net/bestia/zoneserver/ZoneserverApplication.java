package net.bestia.zoneserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.orm.jpa.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories("net.bestia.model")
@EntityScan("net.bestia.model.domain")
@ComponentScan(basePackages={"net.bestia.model.service", "net.bestia.zoneserver"})
public class ZoneserverApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(ZoneserverApplication.class, args);
	}
}
