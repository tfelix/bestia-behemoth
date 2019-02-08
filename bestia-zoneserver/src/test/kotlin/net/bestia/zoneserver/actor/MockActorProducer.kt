package net.bestia.zoneserver.actor

import akka.actor.Actor
import akka.actor.IndirectActorProducer
import com.nhaarman.mockitokotlin2.mock
import org.springframework.context.ApplicationContext

class MockActorProducer(
    private val applicationContext: ApplicationContext,
    private val actorBeanClass: Class<out Actor>,
    private val args: Array<*>
): IndirectActorProducer {

  override fun produce(): Actor {
    return mock()
  }

  override fun actorClass(): Class<out Actor> {
    return actorBeanClass
  }
}