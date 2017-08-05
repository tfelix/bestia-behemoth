package net.bestia.zoneserver;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import net.bestia.zoneserver.actor.SpringExtension;

/**
 * Configures the app context for testing operations.
 * 
 * @author Thomas Felix
 *
 */
@SpringBootConfiguration
public class TestZoneConfiguration {

	@Bean
	@Primary
	public HazelcastInstance hazelcastMock() {
		TestHazelcastInstanceFactory hzFact = new TestHazelcastInstanceFactory();
		HazelcastInstance hz = hzFact.newHazelcastInstance();
		return hz;
	}

	@Bean
	@Primary
	public ActorSystem actorSystem(ApplicationContext appCtx) {
		
		final Config akkaConfig = ConfigFactory.load("akka-test");
		
		final ActorSystem system = ActorSystem.create("testSystem", akkaConfig);

		SpringExtension.PROVIDER.get(system).initialize(appCtx);
		
		return system;
	}
}
