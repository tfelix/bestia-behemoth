package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Cancellable
import mu.KotlinLogging
import net.bestia.messages.MapMoveMessage
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.MoveComponent
import net.bestia.zoneserver.entity.MovingService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

private val LOG = KotlinLogging.logger { }

private class DoMapMove

@Component
@Scope("prototype")
class MoveComponentActor() : AbstractActor() {
  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(MapMoveMessage::class.java, this::handleMapMoveChatCommand)
        .build()
  }

  private fun handleMapMoveChatCommand(msg: MapMoveMessage) {
    val waitResponseProps = AwaitResponseActor.props(listOf(Entity::class)) {
      val moveComponent = it.getResponse(MoveComponent::class)

    }
    val waitResponseActor = context.actorOf(waitResponseProps)
    entityActor.tell(1337L, waitResponseActor)

    val params = mapParamDao.findFirstByOrderByIdDesc()
        ?: throw IllegalStateException("Seems there is no map. Can not determine map size.")

    if (x > params.worldSize.width || y > params.worldSize.height) {
      sendSystemMessage(account.id, "Illegal coordinates. Must be positive and inside the map.")
      return
    }

    val posComp = entityService.getComponent(pbe, PositionComponent::class.java)

    if (posComp.isPresent()) {
      posComp.get().setPosition(x, y)
      LOG.info("GM {} transported entity {} to x: {} y: {}.", account.id, pbe!!.id, x, y)
      entityService.updateComponent(posComp.get())
    } else {
      sendSystemMessage(account.id, "Selected entity has no position component present.")
      return
    }
  }
}

internal data class MapMoveCommand(
    val entity: Entity,
    val point: Point
)

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
class DoMoveActor(
    private val entityActor: ActorRef,
    private val movingService: MovingService
) : AbstractActor() {

  private var tick: Cancellable? = null

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(MapMoveCommand::class.java, this::handleMapMoveCommand)
        .matchEquals(TICK_MSG) { _ -> handleMoveTick() }
        .build()
  }

  override fun preStart() {
    val waitResponseProps = AwaitResponseActor.props(listOf(Entity::class)) {
      val entity = it.getResponse(Entity::class)
      MapMoveCommand(entity, Point(10, 10))
    }
    val waitResponseActor = context.actorOf(waitResponseProps)
    entityActor.tell(1337L, waitResponseActor)

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
  }

  private fun handleMapMoveCommand(msg: MapMoveCommand) {

  }

  /**
   * Move the entity.
   */
  private fun handleMoveTick() {

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
