package net.bestia.zoneserver.rest;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hazelcast.core.HazelcastInstance;

import akka.actor.ActorSystem;


/**
 * Controls the zone server itself.
 * 
 * @author Thomas Felix
 *
 */
@RestController
@RequestMapping("server")
public class ServerController {

	private static final Logger LOG = LoggerFactory.getLogger(ServerController.class);
	
	private final ActorSystem actorSystem;
	private final HazelcastInstance memoryDatabase;
	
	@Autowired
	public ServerController(ActorSystem actorSystem, HazelcastInstance memoryDatabase) {
		
		this.actorSystem = Objects.requireNonNull(actorSystem);
		this.memoryDatabase = Objects.requireNonNull(memoryDatabase);
	}

	/**
	 * Gracefully ends the server and removes it from the cluster. If this is
	 * the last server in the cluster all entities will be persisted to the
	 * persistent storage first.
	 */
	@RequestMapping("/shutdown")
	public void shutdown() {
		LOG.info("Received REST Command: SHUTDOWN.");
		
		// TODO Das hier noch richtig beenden.
		actorSystem.terminate();
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// no op.
		}
		
		memoryDatabase.shutdown();
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// no op.
		}
		
		System.exit(0);
	}
}
