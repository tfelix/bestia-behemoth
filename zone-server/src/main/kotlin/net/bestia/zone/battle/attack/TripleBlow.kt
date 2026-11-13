package net.bestia.zone.battle.attack

import net.bestia.zone.battle.ecs.attack.ComboOpening
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.update
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.Random

/**
 * Triple Blow (`skills.yml` id 144): a landed melee swing may become three blows that deal [DAMAGE_PERCENT] of it in
 * all, `3%/lv` of the time. [AttackExecutionService] asks once per attack. A Chain Combo may follow it for
 * [FOLLOW_UP_MILLIS].
 */
@Component
class TripleBlow(
  private val skillIds: SkillIds,
  private val random: Random = ThreadLocalRandomSource,
  private val clock: Clock = Clock.systemUTC(),
) {

  private val skillId: Long? get() = skillIds[SkillId.M_TRIPLE_BLOW]

  /** Rolls whether the swing [attackerId] just landed becomes a Triple Blow. */
  fun rolls(world: World, attackerId: EntityId): Boolean {
    val level = skillId?.let { world.get(attackerId, KnownSkills::class)?.levelOf(it) } ?: 0
    if (level == 0) return false

    if (random.nextInt(100) >= chancePercent(level)) return false

    world.update(attackerId, { ComboOpening() }) { it.closesAtMillis = clock.millis() + FOLLOW_UP_MILLIS }
    return true
  }

  fun mayFollowUp(world: World, entityId: EntityId): Boolean {
    return world.get(entityId, ComboOpening::class)?.isOpenAt(clock.millis()) == true
  }

  /** Spends the opening of [entityId]'s last Triple Blow on one Chain Combo; false when it had closed. */
  fun spendOpening(world: World, entityId: EntityId): Boolean {
    val opening = world.get(entityId, ComboOpening::class) ?: return false
    if (!opening.isOpenAt(clock.millis())) return false

    opening.closesAtMillis = 0
    return true
  }

  companion object {
    private const val MAX_LEVEL = 10
    const val BLOWS = 3
    const val DAMAGE_PERCENT = 200
    const val FOLLOW_UP_MILLIS = 1500L

    fun chancePercent(level: Int): Int {
      return 3 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
