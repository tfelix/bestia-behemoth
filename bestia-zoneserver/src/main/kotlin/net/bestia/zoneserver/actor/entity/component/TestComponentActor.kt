package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import net.bestia.messages.entity.ComponentEnvelope
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
class TestComponentActor : AbstractActor() {
  override fun createReceive(): Receive {
    return receiveBuilder().match(ComponentEnvelope::class.java, this::handleMessage).build()
  }

  private fun handleMessage(msg: ComponentEnvelope) {

  }
}