package net.bestia.zone.settlement.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.settlement.TownService
import org.springframework.stereotype.Component

@Component
class PlanSettlementHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val towns: TownService,
) : TickMessageHandler<PlanSettlementCMSG> {

  override val wire = decoder(MessageCase.PLAN_SETTLEMENT) { accountId, envelope ->
    PlanSettlementCMSG.fromBnet(accountId, envelope.planSettlement)
  }

  override fun handle(world: World, msg: PlanSettlementCMSG): Boolean {
    towns.plan(msg.playerId, connectionInfoService.getMasterId(msg.playerId), msg.name)

    return true
  }
}
