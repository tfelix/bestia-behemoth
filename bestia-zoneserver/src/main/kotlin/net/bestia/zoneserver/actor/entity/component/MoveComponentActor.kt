package net.bestia.zoneserver.actor.entity.component

import akka.actor.Cancellable
import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.UpdateComponentMessage
import net.bestia.zoneserver.entity.movement.MovingService
import net.bestia.zoneserver.entity.component.MoveComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import java.time.Duration

/**
 * Handle movement of an entity. It will announce the intended move path with
 * timing to all clients in sight so they can start to show the walk animation
 * and will perform the movement timer triggers so the unit does move from tile
 * to tile.
 *
 * @author Thomas Felix
 */
@ActorComponent(MoveComponent::class)
class MoveComponentActor(
    moveComponent: MoveComponent,
    private val movingService: MovingService
) : ComponentActor<MoveComponent>(moveComponent) {

  // TODO Make delta measure real value of tilme
  private val DELTA = 1000f / UPDATE_RATE_HZ

  init {
    timers.startPeriodicTimer(MOVE_TICK_KEY, TICK_MSG, Duration.ofMillis(1000 / UPDATE_RATE_HZ))
  }

  private var tick: Cancellable? = null

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(TICK_MSG) { handleMoveTick() }
  }

  override fun postStop() {
    tick?.cancel()
  }

  private fun handleMoveTick() {
    fetchEntity { entity ->
      val posComp = entity.getComponent(PositionComponent::class.java)
      val d = component.speed * DELTA
      val newPos = posComp.position + d
      // FIXME Do this via a service. Handle collisions with e.g. script entities.
      val newPositionComp = movingService.moveToPosition(entity, newPos)
      context.parent.tell(UpdateComponentMessage(newPositionComp), self)
    }
  }

  companion object {
    private const val UPDATE_RATE_HZ = 5L
    private const val MOVE_TICK_KEY = "MoveTickKey";
    private const val TICK_MSG = "onTick"
    const val NAME = "moveComponent"
  }
}
