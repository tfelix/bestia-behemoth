package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * At the current implementation this actor will only periodically start a short
 * movement for the entity at a random interval.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class AiComponentActor : AbstractActor() {

  override fun createReceive(): AbstractActor.Receive? {
    // TODO Auto-generated method stub
    return null
  }

}
