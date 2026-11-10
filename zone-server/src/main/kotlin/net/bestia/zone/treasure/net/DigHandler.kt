package net.bestia.zone.treasure.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.treasure.DigService
import org.springframework.stereotype.Component

/** Starts a dig for the player's active entity. Digging replaces whatever cast or craft it was busy with. */
@Component
class DigHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val deadActionGuard: DeadActionGuard,
  private val castCancelService: CastCancelService,
  private val digService: DigService,
) : TickMessageHandler<DigCMSG> {
  override val wire = decoder(MessageCase.DIG) { accountId, envelope ->
    DigCMSG.fromBnet(accountId, envelope.dig)
  }

  override fun handle(world: World, msg: DigCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(world, activeEntityId, "dig")) {
      return true
    }

    world.modify(activeEntityId) { id ->
      castCancelService.interrupt(this, id)
      digService.begin(this, id, msg.playerId, msg.position)
    }

    return true
  }
}
