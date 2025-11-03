package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.SelectMasterCMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.ecs.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class SelectMasterHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val masterEntityFactory: MasterEntityFactory,
) : InMessageProcessor.IncomingMessageHandler<SelectMasterCMSG> {
  override val handles = SelectMasterCMSG::class

  override fun handle(msg: SelectMasterCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val masterEntityId = masterEntityFactory.createMasterEntity(msg.selectedMasterId)

    LOG.debug { "Selecting master ${msg.selectedMasterId} with entity id: $masterEntityId for account: ${msg.playerId}" }

    connectionInfoService.activateSession(
      accountId = msg.playerId,
      masterId = msg.selectedMasterId,
      masterEntityId = masterEntityId,
      authorities = emptySet()
    )

    // TODO you probably want to send the client terrain info too immediatly after a master was selected.

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
