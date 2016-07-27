package net.bestia.webserver.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.bestia.server.BestiaActorContext;


@Configuration
public class ApplicationConfig {
	
	@Autowired
	private ApplicationContext springContext;
	
	@Bean
	public BestiaActorContext bestiaActorContext() {
		return new BestiaActorContext(springContext, new ObjectMapper());
	}

}
