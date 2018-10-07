package net.bestia.zoneserver.actor.entity.component

import akka.actor.Cancellable
import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging
import net.bestia.zoneserver.entity.MovingService
import net.bestia.zoneserver.entity.component.MoveComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

private val LOG = KotlinLogging.logger { }

/**
 * Handle movement of an entity. It will announce the intended move path with
 * timing to all clients in sight so they can start to show the walk animation
 * and will perform the movement timer triggers so the unit does move from tile
 * to tile.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
@HandlesComponent(MoveComponent::class)
class MoveComponentActor(
    moveComponent: MoveComponent,
    private val movingService: MovingService
) : ComponentActor<MoveComponent>(moveComponent) {

  private var tick: Cancellable? = null

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(TICK_MSG) { _ -> handleMoveTick() }
  }

  override fun preStart() {
    val nextPos = component.path.peek()

    if (nextPos == null) {
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
    awaitEntity {
      val nextPoint = component.path.poll()
      movingService.moveToPosition(it, nextPoint)

      // Path empty and can we terminate now?
      if (component.path.isEmpty()) {
        context.stop(self)
        return@awaitEntity
      }

      // Here comes the trick: after half the time consider the entity
      // moved/active on the next tile in the path.
      val nextPosition = component.path.peek()
      val moveDelay = movingService.getMoveDelayMs(it, nextPosition) / 2
      LOG.debug { "MoveCompActor: moveTo: $nextPoint in: $moveDelay ms." }
      setupMoveTick(moveDelay)
    }
  }

  /**
   * Setup a new movement tick based on the delay. If the delay is negative we
   * know that we can not move and thus end the movement and this actor.
   */
  private fun setupMoveTick(delayMs: Int) {
    if (delayMs < 0) {
      context.stop(self)
      return
    }

    val shed = context.system().scheduler()
    tick = shed.scheduleOnce(Duration.create(delayMs.toLong(), TimeUnit.MILLISECONDS),
        self, TICK_MSG, context.dispatcher(), null)
  }

  companion object {
    private const val TICK_MSG = "onTick"
    const val NAME = "moveComponent"
  }
}
