package net.bestia.zone.casting

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.Sides
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.script.RestorativeScript
import net.bestia.zone.skill.persistence.SkillReagent
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A potion from the caster's bag, thrown so it works on an ally as if the ally drank it - see `PotionPitcher`.
 *
 * Takes the restorative scripts rather than the item script service: that one also holds the spell scroll script,
 * which casts skills, and a cast reaches this through its [SkillWorld].
 */
@Component
class PotionThrowing(
  restoratives: List<RestorativeScript>,
  private val reagents: ReagentPayment,
) {

  private val potionsByItemId = restoratives.filter { it.isPotion }.associateBy { it.itemId }

  /** Why the caster cannot throw a [itemId] at [targetId], or null when it can. */
  fun refusal(world: World, casterId: EntityId, targetId: EntityId, itemId: Long): OpError? {
    if (itemId !in potionsByItemId || !holds(world, casterId, itemId)) {
      return OpError.SKILL_INVALID_CHOICE
    }

    return if (Sides.areAllied(world, casterId, targetId)) null else OpError.SKILL_NEEDS_ALLY
  }

  /**
   * [targetId] drinks one [itemId] from the caster's bag at [strengthPercent] of its effect. The potion is spent only
   * when it restored something, so one thrown at a body without that vital stays in the bag.
   */
  fun throwAt(world: World, casterId: EntityId, targetId: EntityId, itemId: Long, strengthPercent: Int): Boolean {
    if (refusal(world, casterId, targetId, itemId) != null) {
      return false
    }
    val potion = potionsByItemId.getValue(itemId)
    if (!potion.restore(world, targetId, strengthPercent)) {
      return false
    }

    return reagents.pay(world, casterId, listOf(SkillReagent(itemId = itemId, amount = 1)))
  }

  private fun holds(world: World, casterId: EntityId, itemId: Long): Boolean {
    return world.get(casterId, Inventory::class)?.getItems()?.any { it.itemId == itemId && it.amount > 0 } == true
  }
}
