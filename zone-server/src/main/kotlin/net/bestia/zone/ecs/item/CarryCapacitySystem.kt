package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps [CarryCapacity] telling the truth: `current` mirrors what the entity is actually carrying, `max`
 * follows the attributes and level it actually has.
 *
 * Both used to be written once, at spawn, and never again - this system was referenced from three KDoc
 * blocks but never written. So a limit ignored every level-up and every buff until the player relogged, and
 * `current` was the weight they had when they entered the world, which is what the pickup gate compared
 * against. A session had no carry limit at all.
 *
 * `@Order(61)` is load-bearing. [net.bestia.zone.ecs.core.SystemScheduler] places a system strictly later
 * than any earlier-registered system it conflicts with, so 61 lands after [ObtainItemIntentSystem] (59,
 * writes [Inventory]), after `GainExpSystem` (60, writes [Level]) and after `StatusValueRecalcSystem`
 * (47, writes [StatusValues]) - a pickup, a level-up and an equipment recalc are each reflected in the
 * component the owner is sent on the same tick they happen.
 */
@SpringComponent
@Order(61)
class CarryCapacitySystem(
  private val weightLimitCalculator: WeightLimitCalculator
) : System {

  override val reads: ComponentClassSet = setOf(
    Inventory::class,
    StatusValues::class,
    Level::class
  )
  override val writes: ComponentClassSet = setOf(CarryCapacity::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(CarryCapacity::class).each { id ->
      val capacity = get<CarryCapacity>()

      // Max first: CurMax.max drags current down with it when the ceiling drops, and the inventory is
      // the authority on what is actually carried.
      recomputeMax(world, id, capacity)

      // Unconditional, and deliberately not gated on Inventory.isDirty() - that flag is cleared by the
      // engine after the tick, so reading it here would race the clear. CurMax.current only marks the
      // component dirty when the value really moves, so an untouched inventory costs one sum and sends
      // nothing. This one line is what makes crafting, trade, drop, equip and surveying correct without
      // any of them knowing this component exists.
      world.get(id, Inventory::class)?.let { capacity.current = it.totalWeight }
    }
  }

  /**
   * Reads the *effective* [StatusValues] rather than `BaseStatusValues`, so a strength buff or a piece of
   * gear that grants strength actually lets its wearer carry more - the same choice
   * `StatusValueRecalcSystem` makes for maximum health, mana and stamina. The spawners still seed from the
   * base values, which is why an entity never enters the world with a zero limit.
   *
   * Fetched, not joined into the query: an entity with a [CarryCapacity] but no [StatusValues] must keep the
   * limit it was spawned with instead of dropping out of the loop entirely.
   *
   * For one tick after a level-up this sees the new [Level] against the previous effective attributes -
   * `GainExpSystem` raises the level and asks for a recalc, which `StatusValueRecalcSystem` only performs on
   * the following tick. It converges there, and a level grants status *points* rather than attributes, so
   * there is nothing to see in between.
   */
  private fun recomputeMax(world: World, id: EntityId, capacity: CarryCapacity) {
    val status = world.get(id, StatusValues::class) ?: return
    val level = world.get(id, Level::class)?.level ?: return

    if (capacity.lastKnownStrength == status.strength &&
      capacity.lastKnownVitality == status.vitality &&
      capacity.lastKnownLevel == level
    ) {
      return
    }

    capacity.max = weightLimitCalculator.computeWeightLimit(
      strength = status.strength,
      vitality = status.vitality,
      level = level
    )
    capacity.lastKnownStrength = status.strength
    capacity.lastKnownVitality = status.vitality
    capacity.lastKnownLevel = level
  }
}
