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
class SetInnSavePointHandler(
  private val connections: ConnectionInfoService,
  private val counters: InnCounters,
  private val inn: InnService,
) : TickMessageHandler<SetInnSavePointCMSG> {

  override val wire = decoder(MessageCase.SET_INN_SAVE_POINT) { accountId, envelope ->
    SetInnSavePointCMSG(accountId, envelope.setInnSavePoint.counterEntityId)
  }

  override fun handle(world: World, msg: SetInnSavePointCMSG): Boolean {
    val actor = connections.getActiveEntityId(msg.playerId)
    val counter = counters.inReach(msg.playerId, actor, msg.counterEntityId) ?: return true
    inn.makeSavePoint(msg.playerId, counter)

    return true
  }
}
