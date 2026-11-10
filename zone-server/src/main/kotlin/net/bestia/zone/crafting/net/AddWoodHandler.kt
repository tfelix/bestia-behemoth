package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.prop.CampfireService
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Puts a log on a campfire and answers with the window again, so the player sees the new burn time. */
@Component
class AddWoodHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val campfires: CampfireService,
  private val craftingService: CraftingService,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<AddWoodCMSG> {
  override val wire = decoder(MessageCase.ADD_WOOD) { accountId, envelope ->
    AddWoodCMSG.fromBnet(accountId, envelope.addWood)
  }

  override fun handle(world: World, msg: AddWoodCMSG): Boolean {
    val actorId = connectionInfoService.getActiveEntityId(msg.playerId)

    val denial = campfires.addWood(world, actorId, msg.structureId)
    if (denial != null) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(denial))
    } else {
      val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)
      craftingService.openAtStructure(world, actorId, masterEntityId, msg.structureId)
    }

    return true
  }
}
