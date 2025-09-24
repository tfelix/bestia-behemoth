package net.bestia.zone.message.processor.handler

import com.github.quillraven.fleks.World
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.message.Kill
import net.bestia.zone.ecs.EntityRegistry
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs.battle.Dead
import net.bestia.zone.ecs.visual.BestiaVisual
import org.springframework.stereotype.Component

@Component
class KillHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val entityRegistry: EntityRegistry,
) : IncomingEcsMessageHandler<Kill>() {
  override val handles = Kill::class

  override fun preMessageHandle(msg: Kill): Boolean {
    if (!connectionInfoService.hasAuthority(msg.playerId, Authority.KILL)) {
      LOG.warn { "Player ${msg.playerId} not authorized for KILL" }
      return false
    } else {
      return true
    }
  }

  override fun process(
    world: World,
    msg: Kill
  ) {
    with(world) {
      // you can only kill a mob.
      val killedEntity = entityRegistry.getEntity(msg.entityId)

      if (killedEntity == null) {
        LOG.warn { "Entity ${msg.entityId} not found" }
        return
      }

      if (!killedEntity.has(BestiaVisual)) {
        LOG.warn { "Non mob ${msg.entityId} not killable" }
        return
      }

      killedEntity.configure {
        it += Dead
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
