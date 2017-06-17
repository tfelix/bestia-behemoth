package net.bestia.memoryserver;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Starts the actor system to process bestia messages.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class ZoneStarter implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(ZoneStarter.class);
	
	 @Value("${server.build}")
	private String build;

	@Override
	public void run(String... strings) throws Exception {
		LOG.info("Starting: {}", build);
	}
}
