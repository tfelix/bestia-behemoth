package net.bestia.zone.inn.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.inn.InnCounters
import net.bestia.zone.inn.InnService
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class TendBestiasHandler(
  private val connections: ConnectionInfoService,
  private val counters: InnCounters,
  private val inn: InnService,
) : TickMessageHandler<TendBestiasCMSG> {

  override val wire = decoder(MessageCase.TEND_BESTIAS) { accountId, envelope ->
    TendBestiasCMSG(accountId, envelope.tendBestias.counterEntityId, envelope.tendBestias.expectedPrice)
  }

  override fun handle(world: World, msg: TendBestiasCMSG): Boolean {
    val actor = connections.getActiveEntityId(msg.playerId)
    val counter = counters.inReach(world, msg.playerId, actor, msg.counterEntityId) ?: return true
    inn.tend(world, msg.playerId, counter, msg.expectedPrice)

    return true
  }
}
