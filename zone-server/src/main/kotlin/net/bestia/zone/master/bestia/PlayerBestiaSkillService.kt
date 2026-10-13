package net.bestia.zone.master.bestia

import net.bestia.zone.account.persistence.LearnedSkill
import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Writes the custom skills of a player bestia, the `learned_skill` rows [PlayerBestiaEntitySpawner] reads back. */
@Service
class PlayerBestiaSkillService(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val learnedSkillRepository: LearnedSkillRepository,
  private val skillRepository: SkillRepository,
  private val bestiaCatalogue: BestiaCatalogue,
) {

  /**
   * Teaches the bestia its species' whole learnset at level 1, whatever its own level. A skill it already has a
   * row for is left as it is, so a higher level is never lowered.
   *
   * @return every custom skill the bestia has afterwards, with its level
   */
  @Transactional
  fun learnWholeLearnset(playerBestiaId: PlayerBestiaId): Map<Long, Int> {
    val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)
    val custom = learnedSkillRepository.findAllByPlayerBestiaId(playerBestiaId)
      .associate { it.skill.id to it.level }
      .toMutableMap()

    for (entry in bestiaCatalogue.learnsetUpTo(playerBestia.bestia.id, MAX_BESTIA_LEVEL)) {
      if (entry.skillId in custom) {
        continue
      }

      // Through the repository: `PlayerBestia.learnedSkills` is lazy and `orphanRemoval`, so it is not ours to touch.
      learnedSkillRepository.save(
        LearnedSkill(
          skill = skillRepository.getReferenceById(entry.skillId),
          level = 1,
          playerBestia = playerBestia
        )
      )
      custom[entry.skillId] = 1
    }

    return custom
  }

  companion object {

    /** The top of the learnset a GM grant reaches. No level cap exists elsewhere in the zone to read it from. */
    const val MAX_BESTIA_LEVEL = 100
  }
}
