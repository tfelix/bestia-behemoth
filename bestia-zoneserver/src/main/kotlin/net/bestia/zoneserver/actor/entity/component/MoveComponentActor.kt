package net.bestia.zoneserver.actor.entity.component

import akka.actor.Cancellable
import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.UpdateComponentMessage
import net.bestia.zoneserver.entity.MovingService
import net.bestia.zoneserver.entity.component.MoveComponent
import java.time.Duration

/**
 * Handle movement of an entity. It will announce the intended move path with
 * timing to all clients in sight so they can start to show the walk animation
 * and will perform the movement timer triggers so the unit does move from tile
 * to tile.
 *
 * @author Thomas Felix
 */
@ActorComponent
@HandlesComponent(MoveComponent::class)
class MoveComponentActor(
    moveComponent: MoveComponent,
    private val movingService: MovingService
) : ComponentActor<MoveComponent>(moveComponent) {

  private var tick: Cancellable? = null

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(TICK_MSG) { handleMoveTick() }
  }

  override fun preStart() {
    val nextPos = component.path.firstOrNull() ?: run {
      context.stop(self)
      return
    }

    awaitEntity {
      val moveDelay = movingService.getMoveDelayMs(it, nextPos) / 2
      setupMoveTick(moveDelay)
    }
  }

  override fun postStop() {
    tick?.cancel()
  }

  private fun handleMoveTick() {
    // Path empty and can we terminate now?
    if (component.path.isEmpty()) {
      context.stop(self)
      return
    }

    awaitEntity { entity ->
      val newPosition = component.path[0]

      val newPositionComp = movingService.moveToPosition(entity, newPosition)
      context.parent.tell(UpdateComponentMessage(newPositionComp), self)

      val nextPosition = component.path.getOrNull(1)
          ?: run {
            context.stop(self)
            return@awaitEntity
          }
      val moveDelay = movingService.getMoveDelayMs(entity, nextPosition)
      setupMoveTick(moveDelay)
    }
  }

  /**
   * Setup a new movement tick based on the delay. If the delay is negative we
   * know that we can not move and thus end the movement and this actor.
   */
  private fun setupMoveTick(delayMs: Long) {
    if (delayMs < 0) {
      context.stop(self)
      return
    }

    val shed = context.system().scheduler()
    tick = shed.scheduleOnce(Duration.ofMillis(delayMs),
        self, TICK_MSG, context.dispatcher(), null)
  }

  companion object {
    private const val TICK_MSG = "onTick"
    const val NAME = "moveComponent"
  }
}
