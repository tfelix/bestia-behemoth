package net.bestia.testing;

import org.springframework.context.annotation.Bean;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

public class BasicMocks {

	@Bean
	public HazelcastInstance hazelcastMock() {
		TestHazelcastInstanceFactory hzFact = new TestHazelcastInstanceFactory();
		HazelcastInstance hz = hzFact.newHazelcastInstance();
		return hz;
	}

	@Bean
	public HazelcastInstance hazelcast() {
		Config config = new Config();
		HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);
		return hz;
	}

	@Bean
	public ActorSystem actorSystem() {
		return actorSystemByName("test-system");
	}

	public ActorSystem actorSystemByName(String name) {
		return actorSystem(name);
	}

	public ActorSystem actorSystem(String systemName) {

		final com.typesafe.config.Config akkaConfig = ConfigFactory.empty();
		final ActorSystem system = ActorSystem.create(systemName, akkaConfig);

		// initialize the application context in the Akka Spring extension.
		// SpringExtension.PROVIDER.get(system).initialize(appContext);

		return system;
	}

}
