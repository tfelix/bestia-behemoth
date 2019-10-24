package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.DeleteComponentMessage
import net.bestia.zoneserver.actor.entity.UpdateComponentMessage
import net.bestia.zoneserver.entity.component.AiComponent
import net.bestia.zoneserver.entity.component.MoveComponent
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
    fetchEntity { entity ->
      val speed = 1.5f // entity.getComponent(OriginalStatusComponent::class.java)

      val moveDirectionNormal = when (rand.nextInt(10)) {
        0 -> Vec3(0, -1, 0)
        1 -> Vec3(0, 1, 0)
        2 -> Vec3(1, 0, 0)
        3 -> Vec3(-1, 0, 0)
        4 -> Vec3(1, 1, 0)
        5 -> Vec3(1, -1, 0)
        6 -> Vec3(-1, 1, 0)
        7 -> Vec3(-1, -1, 0)
        else -> null
      }

      if(moveDirectionNormal == null) {
        context.parent.tell(DeleteComponentMessage(MoveComponent::class.java), self)
        return@fetchEntity
      }

      val direction = moveDirectionNormal * speed
      val moveComponent = MoveComponent(entityId = entity.id, speed = direction)

      context.parent.tell(UpdateComponentMessage(moveComponent), self)
    }
  }

  companion object {
    private const val AI_TICK_MSG = "aitick"
  }
}
