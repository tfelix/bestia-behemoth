package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.item.Inventory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps [CarryCapacity] up to date without recomputing it every tick: [CarryCapacity.max] is only
 * recalculated when [Attributes] or [Level] actually changed since the last check (tracked via
 * [CarryCapacity]'s own lastKnown* fields), and [CarryCapacity.current] is only recalculated when
 * [Inventory.isDirty] is true. Runs after [net.bestia.zone.ecs.battle.exp.ExpSystem] (60) so a
 * same-tick level-up is already reflected in [Level].
 */
@SpringComponent
@Order(61)
class CarryCapacitySystem(
  private val carryCapacityService: CarryCapacityService
) : System {

  override val reads: ComponentClassSet = setOf(Attributes::class, Level::class, Inventory::class)
  override val writes: ComponentClassSet = setOf(CarryCapacity::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(CarryCapacity::class, Attributes::class, Level::class, Inventory::class).each { _ ->
      val capacity = get<CarryCapacity>()
      val attributes = get<Attributes>()
      val level = get<Level>()
      val inventory = get<Inventory>()

      if (attributes.strength != capacity.lastKnownStrength ||
        attributes.vitality != capacity.lastKnownVitality ||
        level.level != capacity.lastKnownLevel
      ) {
        capacity.max = carryCapacityService.computeWeightLimit(
          strength = attributes.strength,
          vitality = attributes.vitality,
          level = level.level
        )
        capacity.lastKnownStrength = attributes.strength
        capacity.lastKnownVitality = attributes.vitality
        capacity.lastKnownLevel = level.level
      }

      if (inventory.isDirty()) {
        capacity.current = carryCapacityService.computeCurrentWeight(inventory.getItems())
      }
    }
  }
}
