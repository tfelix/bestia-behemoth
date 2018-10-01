package net.bestia.zoneserver;

import akka.actor.ActorSystem;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.bestia.zoneserver.actor.SpringExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.ActiveProfiles;

/**
 * Configures the app context for testing operations.
 *
 * TODO This file probably needs an overhowl
 * 
 * @author Thomas Felix
 *
 */
@TestConfiguration
@ActiveProfiles("test")
public class TestZoneConfiguration {

	@Bean
	@Primary
	public HazelcastInstance hazelcastMock() {
		TestHazelcastInstanceFactory hzFact = new TestHazelcastInstanceFactory();
		HazelcastInstance hz = hzFact.newHazelcastInstance();
		return hz;
	}

	@Bean
	@Scope("prototype")
	@Primary
	public ActorSystem actorSystem(ApplicationContext appCtx) {
		final Config akkaConfig = ConfigFactory.load("akka-test");
		final ActorSystem system = ActorSystem.create("testSystem", akkaConfig);
		SpringExtension.Companion.initialize(system, appCtx);
		return system;
	}
}
