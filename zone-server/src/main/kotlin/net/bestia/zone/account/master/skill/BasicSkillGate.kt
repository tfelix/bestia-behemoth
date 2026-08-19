package net.bestia.zone.account.master.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Service

/**
 * What a player may do at all, according to how far they have taken Basic Skill.
 *
 * The design docs make Basic Skill a ladder of *permissions* rather than an effect - trading at rank 1, chat
 * at 2, trade posts at 3, sitting recovery at 4, parties and every other skill tree at 5 - so unlike every
 * skill it does nothing on activation and is instead asked about by the subsystems it unlocks.
 *
 * ### Only three of the five are enforced
 *
 * Trading, chat and parties, because they are the only three of the five that exist. There are no trade posts
 * and sitting is not a mechanic - so ranks 3 and 4 have nothing to gate and are deliberately not represented
 * here. A constant for a subsystem that does not exist would read as an implemented rule.
 *
 * ### It answers off the live entity
 *
 * [KnownSkills] on whichever entity the account currently controls, which is the same component
 * `ActivateSkillHandler` validates against - so a rank bought this session counts immediately, and nothing
 * here touches the database on the calling thread. A master with no `KnownSkills` at all (or an account with
 * no live entity) reads as rank 0.
 */
@Service
class BasicSkillGate(
  private val world: WorldView,
  private val connectionInfoService: ConnectionInfoService,
  private val skills: SkillRepository,
) {

  /** Resolved by identifier, because the id in `skills.yml` is content and this is code. */
  private val basicSkillId: Long? by lazy { skills.findByIdentifier(BASIC_SKILL)?.id }

  /** True once the account may trade with another player. Asked of both parties, not only the one who asks. */
  fun mayTrade(accountId: AccountId): Boolean = rankOf(accountId) >= TRADE_RANK

  /** True once the account may talk to other players. Public chat and whispers both; GM commands never. */
  fun mayChat(accountId: AccountId): Boolean = rankOf(accountId) >= CHAT_RANK

  /** True once the account may form or grow a party. */
  fun mayParty(accountId: AccountId): Boolean = rankOf(accountId) >= PARTY_RANK

  /**
   * The Basic Skill rank the account's active entity holds, or 0.
   *
   * Swallows a missing session rather than throwing: a message can arrive from a connection that has just
   * gone away, and "cannot do it" is the right answer for a player who is not there.
   */
  fun rankOf(accountId: AccountId): Int {
    val skillId = basicSkillId
    if (skillId == null) {
      // `skills.yml` has no BASIC_SKILL, which is a catalogue error. Refusing everything on a content
      // mistake would take chat away from every player, so this fails open and says so.
      LOG.warn { "No BASIC_SKILL in the skill catalogue; every Basic Skill gate is open" }
      return Int.MAX_VALUE
    }

    val entityId = try {
      connectionInfoService.getActiveEntityId(accountId)
    } catch (_: Exception) {
      return 0
    }

    return world.read { get(entityId, KnownSkills::class)?.levelOf(skillId) } ?: 0
  }

  companion object {
    /**
     * Trading needs rank 1, chat needs rank 2 and parties need rank 5, per the design docs.
     *
     * **A new master starts with no skill points at all, so none of them is reachable on the first login**,
     * and that is deliberate rather than an oversight: a novice earns the right to be heard, and to be dealt
     * with. The primer the client shows on a master's first login (`DIALOG_BASIC_SKILL_PRIMER_TEXT`) exists to
     * say so, so that being unable to speak reads as a rule rather than as a broken chat box.
     */
    const val TRADE_RANK = 1
    const val CHAT_RANK = 2
    const val PARTY_RANK = 5

    private const val BASIC_SKILL = "BASIC_SKILL"

    private val LOG = KotlinLogging.logger { }
  }
}
