package net.bestia.zone.spellscroll.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.spellscroll.InscriptionOptionsSMSG
import net.bestia.zone.spellscroll.SpellScrollService
import org.springframework.stereotype.Component

/** Lists what the master's Spell Inscription could copy off its bestias, for the picker before the cast. */
@Component
class InscriptionOptionsHandler(
  private val connections: ConnectionInfoService,
  private val scrolls: SpellScrollService,
  private val skillIds: SkillIds,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : IoMessageHandler<InscriptionOptionsCMSG> {

  override val wire = decoder(MessageCase.INSCRIPTION_OPTIONS_REQUEST) { accountId, _ ->
    InscriptionOptionsCMSG(accountId)
  }

  private val inscriptionSkillId: Long? get() = skillIds[SkillId.M_SPELL_INSCRIPTION]

  override fun handle(msg: InscriptionOptionsCMSG): Boolean {
    val skillId = inscriptionSkillId ?: return true
    val masterEntityId = connections.getSelectedMasterEntityId(msg.playerId)

    val options = world.read { scrolls.inscriptionOptions(this, masterEntityId, skillId) }
    out.sendToPlayer(msg.playerId, InscriptionOptionsSMSG(options))

    return true
  }
}
