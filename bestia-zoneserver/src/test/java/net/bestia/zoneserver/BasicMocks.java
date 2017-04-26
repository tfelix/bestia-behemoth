package net.bestia.zoneserver;

import java.net.UnknownHostException;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

@Configuration
@Profile("test")
public class BasicMocks {
	
	@Bean
	public HazelcastInstance hazelcast() {
		TestHazelcastInstanceFactory hzFact = new TestHazelcastInstanceFactory();
    	HazelcastInstance hz = hzFact.newHazelcastInstance();
    	return hz;
	}

	@Bean
	public ActorSystem actorSystem(HazelcastInstance hzInstance, ApplicationContext appContext)
			throws UnknownHostException {

		final Config akkaConfig = ConfigFactory.empty();
		final ActorSystem system = ActorSystem.create("test-cluster", akkaConfig);

		// initialize the application context in the Akka Spring extension.
		//SpringExtension.PROVIDER.get(system).initialize(appContext);

		return system;
	}
	
	

}
