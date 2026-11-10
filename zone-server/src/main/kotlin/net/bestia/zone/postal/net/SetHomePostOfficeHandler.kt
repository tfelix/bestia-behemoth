package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.postal.office.PostOfficeDesk
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.postal.office.PostCounters
import org.springframework.stereotype.Component

@Component
class SetHomePostOfficeHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val counters: PostCounters,
  private val desk: PostOfficeDesk,
) : TickMessageHandler<SetHomePostOfficeCMSG> {

  override val wire = decoder(MessageCase.SET_HOME_POST_OFFICE) { accountId, envelope ->
    SetHomePostOfficeCMSG(accountId, envelope.setHomePostOffice.postmasterEntityId)
  }

  override fun handle(world: World, msg: SetHomePostOfficeCMSG): Boolean {
    val actor = connectionInfoService.getActiveEntityId(msg.playerId)
    val counter = counters.inReach(world, msg.playerId, actor, msg.postmasterEntityId) ?: return true
    desk.setHome(msg.playerId, counter)

    return true
  }
}
