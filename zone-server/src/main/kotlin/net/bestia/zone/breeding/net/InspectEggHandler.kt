package net.bestia.zone.breeding.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.breeding.BestiaEggSMSG
import net.bestia.zone.breeding.EggSummary
import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.master.skill.BloodlineStudy
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/** Answers what an egg in the master's own bag holds, read as the master can read it. */
@Component
class InspectEggHandler(
  private val connections: ConnectionInfoService,
  private val inventoryService: InventoryService,
  private val eggs: BestiaEggRepository,
  private val bloodlineStudy: BloodlineStudy,
  private val out: OutMessageProcessor,
) : IoMessageHandler<InspectEggCMSG> {
  override val wire = decoder(MessageCase.INSPECT_EGG) { accountId, envelope ->
    InspectEggCMSG.fromBnet(accountId, envelope.inspectEgg)
  }

  override fun handle(msg: InspectEggCMSG): Boolean {
    val masterId = connections.getMasterId(msg.playerId)
    val egg = eggs.findByIdOrNull(msg.uniqueId)?.takeIf { inventoryService.heldInstance(masterId, msg.uniqueId) != null }
    if (egg == null) {
      LOG.debug { "Master $masterId asked about egg ${msg.uniqueId}, which it does not hold" }
      return true
    }

    val readsExactIvs = BloodlineStudy.readsExactIvs(bloodlineStudy.levelOf(masterId))
    out.sendToPlayer(msg.playerId, BestiaEggSMSG(EggSummary.of(egg).toBnet(readsExactIvs)))
    return true
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
