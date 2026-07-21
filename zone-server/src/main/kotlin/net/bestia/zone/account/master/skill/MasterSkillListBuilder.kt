package net.bestia.zone.account.master.skill

import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.skill.LearnedSkillRepository
import net.bestia.zone.skill.SkillListSMSG
import org.springframework.stereotype.Service

/**
 * Builds a master's merged skill list - the whole skill tree (config, not player state), every
 * node shown and dimmed on the client until points are invested into it. Shared by
 * [net.bestia.zone.skill.GetSkillsHandler] (client-requested refresh) and [InvestSkillPointHandler] (proactive push
 * right after an investment is confirmed) so both read the same source of truth.
 */
@Service
class MasterSkillListBuilder(
  private val masterRepository: MasterRepository,
  private val learnedSkillRepository: LearnedSkillRepository,
  private val masterSkillTreeRegistry: MasterSkillTreeRegistry
) {
  fun buildMasterSkillEntries(masterId: Long): List<SkillListSMSG.SkillListEntry> {
    // safety check if the master exists we can not see this alone by selecting all learned skills
    masterRepository.findByIdOrThrow(masterId)

    val learnedBySkillId = learnedSkillRepository.findAllByMasterId(masterId)
      .associateBy { it.skill.id }

    return masterSkillTreeRegistry.all().map { node ->
      val investedLevel = learnedBySkillId[node.skillId]?.level ?: 0
      SkillListSMSG.SkillListEntry(
        skillId = node.skillId,
        level = investedLevel,
      )
    }
  }
}
