package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.SpeedComponent
import net.bestia.zoneserver.entity.movement.MovingService
import java.time.Duration

/**
 * Handle movement of an entity. It will announce the intended move path with
 * timing to all clients in sight so they can start to show the walk animation
 * and will perform the movement timer triggers so the unit does move from tile
 * to tile.
 *
 * @author Thomas Felix
 */
@ActorComponent(SpeedComponent::class)
class SpeedComponentActor(
    moveComponent: SpeedComponent,
    private val movingService: MovingService
) : ComponentActor<SpeedComponent>(moveComponent) {

  init {
    timers.startTimerWithFixedDelay(MOVE_TICK_KEY, TICK_MSG, TICK_DELAY)
  }

  private var lastTick = System.currentTimeMillis()

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(TICK_MSG) { handleMoveTick() }
  }

  private fun handleMoveTick() {
    requestOwnerEntity { entity ->
      val now = System.currentTimeMillis()
      val delta = now - lastTick
      lastTick = now

      val speedComp = entity.getComponent(SpeedComponent::class.java)
      val deltaPos = speedComp.speed * (delta / 1000f)

      val updatePosition = SetPositionToOffset(entity.id, deltaPos)
      context.parent.tell(updatePosition, self)
    }
  }

  companion object {
    private const val UPDATE_RATE_HZ = 5L
    private val TICK_DELAY = Duration.ofMillis(1000 / UPDATE_RATE_HZ)
    private const val MOVE_TICK_KEY = "MoveTickKey";
    private const val TICK_MSG = "onTick"
    const val NAME = "moveComponent"
  }
}
