package net.bestia.zone.battle.attack

import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Triple Blow (`skills.yml` id 144): a landed melee swing may become three blows that deal [DAMAGE_PERCENT] of it in
 * all, `3%/lv` of the time. [AttackExecutionService] asks once per attack.
 */
@Component
class TripleBlow(
  private val skills: SkillRepository,
  private val random: Random = ThreadLocalRandomSource,
) {

  private val skillId: Long? by lazy { skills.findByIdentifier(SkillId.M_TRIPLE_BLOW)?.id }

  fun warmUp() {
    skillId
  }

  /** Rolls whether the swing [attackerId] just landed becomes a Triple Blow. */
  fun rolls(world: World, attackerId: EntityId): Boolean {
    val level = skillId?.let { world.get(attackerId, KnownSkills::class)?.levelOf(it) } ?: 0
    if (level == 0) return false

    return random.nextInt(100) < chancePercent(level)
  }

  companion object {
    private const val MAX_LEVEL = 10
    const val BLOWS = 3
    const val DAMAGE_PERCENT = 200

    fun chancePercent(level: Int): Int {
      return 3 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
