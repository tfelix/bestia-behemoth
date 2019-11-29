package net.bestia.zoneserver.actor

import akka.actor.*
import akka.actor.Actor
import com.nhaarman.mockitokotlin2.mock
import org.springframework.context.ApplicationContext

class MockActorProducer(
    private val actorBeanClass: Class<out Actor>
): IndirectActorProducer {

  /**
   * It is used by Spring. We need this ctor as Spring internally matches it against this signature.
   * If it has not this signature spring wont instance this MockActorProducer.
   */
  @Suppress("unused")
  constructor(
      applicationContext: ApplicationContext,
      actorBeanClass: Class<out Actor>,
      args: Array<*>
  ) : this(actorBeanClass)

  override fun produce(): Actor {
    return mock()
  }

  override fun actorClass(): Class<out Actor> {
    return actorBeanClass
  }
}
