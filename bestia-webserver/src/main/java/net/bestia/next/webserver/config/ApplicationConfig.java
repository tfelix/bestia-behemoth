package net.bestia.next.webserver.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bestia.next.actor.BestiaActorContext;

@Configuration
public class ApplicationConfig {
	
	@Autowired
	private ApplicationContext springContext;
	
	@Bean
	public BestiaActorContext bestiaActorContext() {
		return new BestiaActorContext(springContext);
	}

}
