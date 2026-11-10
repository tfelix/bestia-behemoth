package net.bestia.zone.fishing.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.fishing.FishingService
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Nothing to report: the catch shows up as an inventory gain, and the bobber vanishes either way. */
@Component
class StrikeLineHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val fishingService: FishingService,
) : TickMessageHandler<StrikeLineCMSG> {
  override val wire = decoder(MessageCase.STRIKE_LINE) { accountId, _ -> StrikeLineCMSG(accountId) }

  override fun handle(world: World, msg: StrikeLineCMSG): Boolean {
    fishingService.strike(world, connectionInfoService.getActiveEntityId(msg.playerId))

    return true
  }
}
