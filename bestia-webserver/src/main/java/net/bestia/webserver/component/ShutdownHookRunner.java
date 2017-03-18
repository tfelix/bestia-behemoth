package net.bestia.webserver.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import net.bestia.webserver.actor.ActorSystemTerminator;

/**
 * This will register a shutdown hook the the webserver, if we are terminated
 * properly we will terminate our connection gracefully with the system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class ShutdownHookRunner implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(ShutdownHookRunner.class);

	private ActorSystem system;
	private ActorSystemTerminator systemTerminator;

	@Autowired
	public void setSystem(ActorSystem system) {
		this.system = system;
	}
	
	@Autowired
	public void setSystemTerminator(ActorSystemTerminator systemTerminator) {
		this.systemTerminator = systemTerminator;
	}

	@Override
	public void run(String... arg0) throws Exception {
		LOG.info("Registering VM shutdown hook.");

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				LOG.info("Shutdown signal received. Now terminating.");
				
				final Cluster cluster = Cluster.get(system);
				cluster.leave(cluster.selfAddress());
				
				// This will end the actor system and hazelcast.
				systemTerminator.run();
			}
		});

	}

}
