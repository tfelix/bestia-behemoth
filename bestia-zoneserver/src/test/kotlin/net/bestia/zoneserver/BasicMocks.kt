package net.bestia.zoneserver

import org.springframework.context.annotation.Bean

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem

class BasicMocks {

  @Bean
  fun actorSystem(): ActorSystem {
    return actorSystemByName("test-system")
  }

  fun actorSystemByName(name: String): ActorSystem {
    return actorSystem(name)
  }

  fun actorSystem(systemName: String): ActorSystem {

    val akkaConfig = ConfigFactory.empty()

    // initialize the application context in the Akka Spring extension.
    // SpringExtension.PROVIDER.get(system).initialize(appContext);

    return ActorSystem.create(systemName, akkaConfig)
  }

}
