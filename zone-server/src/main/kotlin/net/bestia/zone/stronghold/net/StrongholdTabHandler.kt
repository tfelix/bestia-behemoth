package net.bestia.zone.stronghold.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.stronghold.StrongholdTab
import org.springframework.stereotype.Component

/** The guild window's Stronghold tab. It acts for the player's master, whichever entity they drive. */
@Component
class StrongholdTabHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val tab: StrongholdTab,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<StrongholdTabCMSG> {

  override val wire = decoder(MessageCase.STRONGHOLD_TAB_REQUEST) { accountId, envelope ->
    StrongholdTabCMSG.fromBnet(accountId, envelope.strongholdTabRequest)
  }

  override fun handle(world: World, msg: StrongholdTabCMSG): Boolean {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)

    val refusal = when (msg.action) {
      StrongholdTabCMSG.Action.SHOW -> null
      StrongholdTabCMSG.Action.COLLECT -> tab.collect(world, masterEntityId, msg.rank)
      StrongholdTabCMSG.Action.SEND_STORAGE_AWAY -> tab.sendStorageAway(world, masterEntityId)
    }

    refusal?.let { outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }
    tab.show(world, masterEntityId)

    return true
  }
}
