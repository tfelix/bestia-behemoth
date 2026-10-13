package net.bestia.zone.chat.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.master.bestia.PlayerBestiaSkillService
import net.bestia.zone.master.skill.MasterSkillTreeService
import net.bestia.zone.master.skill.SkillListSMSG
import net.bestia.zone.master.skill.SkillListService
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Gives the active entity every skill it can have, for testing without the points or levels they cost.
 *
 * A master gets each node of the skill tree at its max level. A bestia learns its species' whole learnset at level 1
 * as custom skills, which is what makes the grant outlive a restart.
 */
@Component
class AllSkillsChatCommand(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val masterSkillTreeService: MasterSkillTreeService,
  private val playerBestiaSkillService: PlayerBestiaSkillService,
  private val skillListService: SkillListService,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.SKILLS

  override fun getHelpText(): String {
    return "/allskills - Master: every skill at max level. Bestia: its whole learnset, learned at level 1."
  }

  override fun isMatch(cmdText: String): Boolean {
    return cmdText.trim() == COMMAND
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(playerId)
    val isMaster = activeEntityId == connectionInfoService.getSelectedMasterEntityId(playerId)

    val summary = if (isMaster) {
      "Raised ${raiseMaster(playerId, activeEntityId)} skills to max level."
    } else {
      "Learned ${learnBestia(playerId, activeEntityId)} new skills."
    }
    LOG.info { "/allskills for player $playerId on entity $activeEntityId: $summary" }

    // Pushed here because the Skills window only asks for the list when it is opened.
    val entries = skillListService.entriesFor(playerId, activeEntityId)
    out.sendToPlayer(playerId, SkillListSMSG(activeEntityId, entries))
    out.sendToPlayer(playerId, ChatSMSG(text = summary, type = ChatType.COMMAND))

    return true
  }

  private fun raiseMaster(playerId: Long, entityId: EntityId): Int {
    val raised = world.modify(entityId) { id -> masterSkillTreeService.grantAll(this, playerId, id) }

    return checkNotNull(raised) { "Master entity $entityId of player $playerId is not in the world" }.size
  }

  private fun learnBestia(playerId: Long, entityId: EntityId): Int {
    val playerBestiaId = checkNotNull(connectionInfoService.getActivePlayerBestiaId(playerId)) {
      "Active entity $entityId of player $playerId is neither the master nor an owned bestia"
    }

    // The database write comes first and outside the world scope: a scope holds the tick, and this one reads rows.
    val learned = playerBestiaSkillService.learnWholeLearnset(playerBestiaId)

    val raised = world.modify(entityId) { id ->
      val known = checkNotNull(get(id, KnownSkills::class)) { "Bestia entity $id has no KnownSkills" }
      val newlyKnown = learned.filter { (skillId, level) -> known.levelOf(skillId) < level }

      newlyKnown.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
      // A learned passive feeds the status recalc, so the effective values are now stale.
      if (newlyKnown.isNotEmpty()) {
        add(id, IsStatusValueDirty)
      }

      newlyKnown.size
    }

    return checkNotNull(raised) { "Bestia entity $entityId of player $playerId is not in the world" }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    private const val COMMAND = "/allskills"
  }
}
