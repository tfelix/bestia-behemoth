package net.bestia.webserver.actor;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import akka.actor.ActorSystem;


/**
 * Initializes and setup the actor system.
 * 
 * @author Thomas Felix
 *
 */
@Component
public final class WebStarter implements CommandLineRunner {
	
	private static final Logger LOG = LoggerFactory.getLogger(WebStarter.class);
	
	private final ActorSystem system;
	
	@Autowired
	public WebStarter(ActorSystem system) {
		
		this.system = Objects.requireNonNull(system);
	}

	@Override
	public void run(String... args) throws Exception {
		LOG.info("Starting webserver actor system...");

	}

}
