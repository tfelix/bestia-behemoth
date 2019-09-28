package net.bestia.zoneserver.actor

import akka.actor.*
import akka.actor.Actor
import com.nhaarman.mockitokotlin2.mock

class MockActorProducer(
    private val actorBeanClass: Class<out Actor>
): IndirectActorProducer {

  override fun produce(): Actor {
    return mock()
  }

  override fun actorClass(): Class<out Actor> {
    return actorBeanClass
  }
}
