package net.bestia.zone.battle.attack

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.ToolKind
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.skill.OwnerSkills
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * What it takes to fell a tree: an axe or a felling tool in hand, and Lumberjack to bring it down sooner -
 * `-10%/lv` of the time, which is that much more of each blow landing on the trunk.
 */
@Component
class Felling(private val skillIds: SkillIds) {

  private val lumberjackId: Long? get() = skillIds[SkillId.M_LUMBERJACK]

  val reads: ComponentClassSet = setOf(StaticVisual::class, CombatStats::class) + OwnerSkills.READS

  fun isTree(world: World, targetId: EntityId): Boolean {
    return world.get(targetId, StaticVisual::class)?.kind in TREE_KINDS
  }

  fun canFell(world: World, attackerId: EntityId): Boolean {
    val combat = world.get(attackerId, CombatStats::class)?.values ?: return false

    return combat.heldTool == ToolKind.FELLING || combat.heldWeaponType in AXES
  }

  /** [amount] of a blow on a tree, after the attacker's Lumberjack. */
  fun felled(world: World, attackerId: EntityId, amount: Int): Int {
    val level = lumberjackId?.let { OwnerSkills.levelOf(world, attackerId, it) } ?: 0

    return amount * 100 / (100 - TIME_PERCENT_PER_LEVEL * level.coerceIn(0, MAX_LEVEL))
  }

  private companion object {
    val TREE_KINDS = setOf(StaticEntityKind.TREE, StaticEntityKind.BLIGHTED_TREE)
    val AXES = setOf(WeaponType.AXE, WeaponType.GREATAXE)
    const val TIME_PERCENT_PER_LEVEL = 10
    const val MAX_LEVEL = 5
  }
}
