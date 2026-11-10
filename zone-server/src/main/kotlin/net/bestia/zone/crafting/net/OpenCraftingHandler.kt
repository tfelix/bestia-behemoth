package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Answers the window opening with what the active entity can make where it stands. */
@Component
class OpenCraftingHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val craftingService: CraftingService,
) : TickMessageHandler<OpenCraftingCMSG> {
  override val wire = decoder(MessageCase.OPEN_CRAFTING) { accountId, envelope ->
    OpenCraftingCMSG.fromBnet(accountId, envelope.openCrafting)
  }

  override fun handle(world: World, msg: OpenCraftingCMSG): Boolean {
    val crafterId = connectionInfoService.getActiveEntityId(msg.playerId)
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)

    if (msg.structureId != 0L) {
      craftingService.openAtStructure(world, crafterId, masterEntityId, msg.structureId)
    } else {
      craftingService.openCrafting(world, crafterId, masterEntityId, msg.skillId)
    }

    return true
  }
}
