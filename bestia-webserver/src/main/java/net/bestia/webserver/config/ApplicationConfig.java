package net.bestia.webserver.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bestia.server.BestiaActorContext;


@Configuration
public class ApplicationConfig {
	
	@Bean
	public BestiaActorContext bestiaActorContext(ApplicationContext springContext) {
		return new BestiaActorContext(springContext);
	}

}
