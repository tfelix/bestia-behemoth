package net.bestia.zone.battle.status.scripts

import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * What a Bard's two song passives add to every song: Voice Training (`skills.yml` id 165) a wider field and a longer
 * song, Grand Performance (id 170) a stronger effect. Read off the Bard while the song plays.
 */
@Component
class Songcraft(private val skills: SkillRepository) {

  private val voiceTrainingId: Long? by lazy { skills.findByIdentifier(SkillId.M_VOICE_TRAINING)?.id }
  private val grandPerformanceId: Long? by lazy { skills.findByIdentifier(SkillId.M_GRAND_PERFORMANCE)?.id }

  fun warmUp() {
    voiceTrainingId
    grandPerformanceId
  }

  fun radiusTiles(world: World, bardId: EntityId): Long {
    return BASE_RADIUS_TILES + levelOf(world, bardId, voiceTrainingId)
  }

  fun durationSeconds(world: World, bardId: EntityId, baseSeconds: Double): Double {
    return baseSeconds * (100 + DURATION_PERCENT_PER_LEVEL * levelOf(world, bardId, voiceTrainingId)) / 100
  }

  /** How strong the Bard's songs are, in percent of their table. */
  fun effectPercent(world: World, bardId: EntityId): Int {
    return 100 + EFFECT_PERCENT_PER_LEVEL * levelOf(world, bardId, grandPerformanceId)
  }

  private fun levelOf(world: World, bardId: EntityId, skillId: Long?): Int {
    skillId ?: return 0

    return world.get(bardId, KnownSkills::class)?.levelOf(skillId) ?: 0
  }

  companion object {
    const val BASE_RADIUS_TILES = 3L
    private const val DURATION_PERCENT_PER_LEVEL = 10
    private const val EFFECT_PERCENT_PER_LEVEL = 10

    /** [base] scaled by an [effectPercent] a song handed out; 0 is a song without one, at full table strength. */
    fun boosted(base: Int, effectPercent: Int): Int {
      val percent = if (effectPercent == 0) 100 else effectPercent

      return base * percent / 100
    }
  }
}
