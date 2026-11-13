package net.bestia.zone.prop

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.skill.OwnerSkills
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Which herbs a picker may take: up to the level Herbalism allows, its own or, for a bestia, its master's. The herb's
 * level is the item level of what it yields.
 */
@Component
class HerbPicking(
  private val skillIds: SkillIds,
  private val items: ItemTemplateRegistry,
) {

  private val herbalismId: Long? get() = skillIds[SkillId.M_HERBALISM]

  val reads: ComponentClassSet = OwnerSkills.READS

  fun mayPick(world: World, pickerId: EntityId, kind: StaticEntityKind, yieldItemId: Long): Boolean {
    if (kind !in HERBS) return true
    val herbLevel = items.baseLevelOf(yieldItemId) ?: return true
    val herbalism = herbalismId?.let { OwnerSkills.levelOf(world, pickerId, it) } ?: 0

    return herbLevel <= maxHerbLevel(herbalism)
  }

  companion object {
    val HERBS = setOf(
      StaticEntityKind.HERB, StaticEntityKind.BLIGHTED_HERB, StaticEntityKind.MEDICINAL_HERB,
      StaticEntityKind.MANA_HERB, StaticEntityKind.FROST_HERB, StaticEntityKind.SUN_HERB, StaticEntityKind.GHOST_HERB
    )

    /** 10 untrained, which is the wild herb alone; then 20 per level; every herb at the top level. */
    fun maxHerbLevel(herbalism: Int): Int = when {
      herbalism <= 0 -> UNTRAINED_MAX_LEVEL
      herbalism >= MAX_SKILL_LEVEL -> Int.MAX_VALUE
      else -> herbalism * LEVELS_PER_RANK
    }

    private const val UNTRAINED_MAX_LEVEL = 10
    private const val LEVELS_PER_RANK = 20
    private const val MAX_SKILL_LEVEL = 5
  }
}
