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
class RefuseParcelHandler(
  private val connections: ConnectionInfoService,
  private val counters: PostCounters,
  private val desk: PostOfficeDesk,
) : TickMessageHandler<RefuseParcelCMSG> {

  override val wire = decoder(MessageCase.REFUSE_PARCEL) { accountId, envelope ->
    RefuseParcelCMSG(accountId, envelope.refuseParcel.postmasterEntityId, envelope.refuseParcel.parcelId)
  }

  override fun handle(world: World, msg: RefuseParcelCMSG): Boolean {
    val actor = connections.getActiveEntityId(msg.playerId)
    val counter = counters.inReach(world, msg.playerId, actor, msg.postmasterEntityId) ?: return true
    desk.refuseParcel(msg.playerId, counter, msg.parcelId)

    return true
  }
}
