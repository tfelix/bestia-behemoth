package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.crafting.repair.RepairService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class RepairStructureHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val repairs: RepairService,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<RepairStructureCMSG> {

  override val wire = decoder(MessageCase.REPAIR_STRUCTURE) { accountId, envelope ->
    RepairStructureCMSG.fromBnet(accountId, envelope.repairStructure)
  }

  override fun handle(world: World, msg: RepairStructureCMSG): Boolean {
    val actorId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)

    repairs.toggle(world, actorId, msg.entityId)?.let { outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }

    return true
  }
}
