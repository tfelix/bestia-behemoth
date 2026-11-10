package net.bestia.zone.settlement.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.settlement.TownService
import org.springframework.stereotype.Component

@Component
class TearDownSettlementHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val towns: TownService,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<TearDownSettlementCMSG> {

  override val wire = decoder(MessageCase.TEAR_DOWN_SETTLEMENT) { accountId, envelope ->
    TearDownSettlementCMSG.fromBnet(accountId, envelope.tearDownSettlement)
  }

  override fun handle(world: World, msg: TearDownSettlementCMSG): Boolean {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)

    towns.tearDown(world, masterEntityId, msg.signEntityId)
      ?.let { outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }

    return true
  }
}
