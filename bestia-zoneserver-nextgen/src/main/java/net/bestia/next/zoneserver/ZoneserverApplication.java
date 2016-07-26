package net.bestia.next.zoneserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import net.bestia.model.dao.AccountDAO;

@SpringBootApplication
@ComponentScan(basePackageClasses={AccountDAO.class})
public class ZoneserverApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(ZoneserverApplication.class, args);
	}
}
