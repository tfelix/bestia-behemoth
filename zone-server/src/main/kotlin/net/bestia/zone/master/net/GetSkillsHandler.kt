package net.bestia.zone.master.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component
import net.bestia.zone.master.skill.SkillListSMSG
import net.bestia.zone.master.skill.SkillListService

/**
 * Sends the skill list of the account's currently active entity straight back, rather than relying on the ECS
 * dirty-sync pipeline.
 */
@Component
class GetSkillsHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
  private val skillListService: SkillListService,
) : IoMessageHandler<GetSkillsCMSG> {
  override val wire = decoder(MessageCase.GET_SKILLS) { accountId, _ -> GetSkillsCMSG(accountId) }

  override fun handle(msg: GetSkillsCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    val entries = skillListService.entriesFor(msg.playerId, activeEntityId)

    outMessageProcessor.sendToPlayer(msg.playerId, SkillListSMSG(activeEntityId, entries))

    return true
  }
}
