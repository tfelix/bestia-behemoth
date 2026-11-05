package net.bestia.zone.companion.query

import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry
import org.springframework.stereotype.Component

/** The Basic Skill rank of a master the app acts as, which may or may not be played right now. */
@Component
class MasterBasicSkill(
  private val skillGate: BasicSkillGate,
  private val sessions: ConnectionInfoService,
  private val skillTree: MasterSkillTreeRegistry,
  private val learnedSkills: LearnedSkillRepository,
) {

  fun mayChat(accountId: Long, masterId: Long): Boolean {
    return rankOf(accountId, masterId) >= BasicSkillGate.CHAT_RANK
  }

  /**
   * [masterId]'s Basic Skill rank. [BasicSkillGate] answers off the live master, which only exists while it is
   * played, and then it is the one to ask: a rank bought this session is not stored yet. Otherwise the master's
   * stored rank, as of its last save.
   */
  private fun rankOf(accountId: Long, masterId: Long): Int {
    if (sessions.isPlaying(accountId, masterId)) {
      return skillGate.rankOf(accountId)
    }

    // No Basic Skill in the tree: the gate's own answer, which fails open.
    val skillId = skillTree.basicSkillId ?: return skillGate.rankOf(accountId)

    return learnedSkills.findByMasterIdAndSkillId(masterId, skillId)?.level ?: 0
  }
}
