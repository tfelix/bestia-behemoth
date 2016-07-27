package net.bestia.server;

import java.util.Objects;

import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BestiaActorContext {
	
	private final ObjectMapper jsonMapper;
	private final ApplicationContext springContext;

	public BestiaActorContext(ApplicationContext springContext, ObjectMapper jsonMapper) {
		
		this.jsonMapper = Objects.requireNonNull(jsonMapper);
		this.springContext = Objects.requireNonNull(springContext);
	}
	
	public ObjectMapper getJsonMapper() {
		return jsonMapper;
	}
	
	public ApplicationContext getSpringContext() {
		return springContext;
	}

}
