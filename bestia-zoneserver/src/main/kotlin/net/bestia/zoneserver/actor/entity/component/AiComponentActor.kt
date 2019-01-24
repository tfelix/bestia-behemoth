package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.UpdateComponentMessage
import net.bestia.zoneserver.entity.component.AiComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import java.time.Duration
import java.util.*

/**
 * At the current implementation this actor will only periodically start a short
 * movement for the entity at a random interval.
 *
 * @author Thomas Felix
 */
@ActorComponent(AiComponent::class)
class AiComponentActor(
    aiComponent: AiComponent
) : ComponentActor<AiComponent>(aiComponent) {

  private val rand = Random()

  private val tick = context.system().scheduler().schedule(
      Duration.ofSeconds(5),
      Duration.ofSeconds(5),
      self,
      AI_TICK_MSG,
      context.dispatcher(),
      null
  )

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(AI_TICK_MSG) { handleAiTick() }
  }

  override fun postStop() {
    tick.cancel()
  }

  private fun handleAiTick() {
    fetchEntity {
      val positionComponent = it.getComponent(PositionComponent::class.java)

      val moveDelta = when (rand.nextInt(10)) {
        0 -> Point(0, -1)
        1 -> Point(0, 1)
        2 -> Point(1, 0)
        3 -> Point(-1, 0)
        4 -> Point(1, 1)
        5 -> Point(1, -1)
        6 -> Point(-1, 1)
        7 -> Point(-1, -1)
        else -> Point(0, 0)
      }

      val newPos = positionComponent.position - moveDelta
      val newPositionComp = positionComponent.copy(shape = positionComponent.shape.moveTo(newPos))
      context.parent.tell(UpdateComponentMessage(newPositionComp), self)
    }
  }

  companion object {
    private const val AI_TICK_MSG = "aitick"
  }
}
