package net.bestia.zone.casting.essence

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.SkillGrant
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A Learned Trick essence: its wearer may cast one attack of the species at skill level 1, as long as they wear it,
 * like a spell scroll that never runs out. An attack learned above the best gem's cap is not granted.
 */
@Component
class LearnedTricks(
  private val skillIds: SkillIds,
  private val bestias: BestiaCatalogue,
) : SkillGrant {

  override fun grantedLevel(world: World, casterId: EntityId, skillId: Long): Int {
    return if (skillId in of(world, casterId)) SKILL_LEVEL else 0
  }

  /** The skill ids [entityId] may cast through the essences it wears. */
  fun of(world: World, entityId: EntityId): List<Long> {
    val tricks = world.get(entityId, CombatStats::class)?.values?.learnedTricks.orEmpty()

    return tricks.mapNotNull { trick ->
      val skillId = skillIds.idOf(trick.skill) ?: return@mapNotNull null
      skillId.takeIf { (bestias.attackLevelOf(it) ?: 0) <= trick.attackLevelCap }
    }
  }

  companion object {
    const val SKILL_LEVEL = 1
  }
}
