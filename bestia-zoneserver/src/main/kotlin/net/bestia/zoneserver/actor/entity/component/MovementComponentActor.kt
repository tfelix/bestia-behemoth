package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.actor.Cancellable
import mu.KotlinLogging
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.MoveComponent
import net.bestia.zoneserver.entity.MovingService
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
class MovementComponentActor(
        private val entityId: Long,
        private val movingService: MovingService,
        private val entityService: EntityService
) : AbstractActor() {

  private var tick: Cancellable? = null

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .matchEquals(TICK_MSG) { x -> handleMoveTick() }
            .build()
  }

  override fun preStart() {

    val optMc = entityService.getComponent(entityId, MoveComponent::class.java)

    if (optMc.isPresent) {
      val nextPos = optMc.get().path.peek()
      val moveDelay = movingService.getMoveDelayMs(entityId, nextPos) / 2
      setupMoveTick(moveDelay)
    } else {
      context.stop(self)
    }
  }

  override fun postStop() {

    if (tick != null) {
      tick!!.cancel()
    }

    entityService.deleteComponent(entityId, MoveComponent::class.java)
  }

  /**
   * Move the entity.
   */
  private fun handleMoveTick() {

    // TODO Diese logik evtl in den MovingService überführen?
    val optMc = entityService.getComponent(entityId, MoveComponent::class.java)

    if (optMc.isPresent) {
      val path = optMc.get().path
      val nextPoint = path.poll()

      movingService.moveToPosition(entityId, nextPoint)

      // Path empty and can we terminate now?
      if (path.isEmpty()) {
        context.stop(self)
      } else {
        // Here comes the trick: after half the time consider the entity
        // moved/active on the next tile in the path.
        val moveDelay = movingService.getMoveDelayMs(entityId, path.peek()) / 2
        LOG.debug("MoveCompActor: moveTo: {}, nextMove: {} in: {} ms.", nextPoint, path.peek(), moveDelay)
        setupMoveTick(moveDelay)
        // Save the updated move component.
        entityService.updateComponent(optMc.get())
      }
    } else {
      context.stop(self)
    }
  }

  /**
   * Setup a new movement tick based on the delay. If the delay is negative we
   * know that we can not move and thus end the movement and this actor.
   *
   * @param delayMs
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
