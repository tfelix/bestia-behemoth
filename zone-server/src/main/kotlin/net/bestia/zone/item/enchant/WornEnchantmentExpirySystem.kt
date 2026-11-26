package net.bestia.zone.item.enchant

import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import java.time.Clock
import org.springframework.stereotype.Component as SpringComponent

/**
 * Takes ended enchantments off worn gear, so an endow stops striking with its element when its time is up. Nothing
 * ticks an item, and only worn gear changes what its wearer does. The ended row is left to [ItemEnchantmentSweep].
 */
@SpringComponent
class WornEnchantmentExpirySystem(
  private val clock: Clock = Clock.systemUTC(),
) : System {

  override val phase = Phase.STATUS
  // Before the recalc, so it sees the endow gone in the same tick. The auras order themselves before this.
  override val after = setOf(StatusEffectDurationSystem::class)
  override val before = setOf(StatusValueRecalcSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val writes: ComponentClassSet = setOf(Equipment::class, Inventory::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    val now = clock.instant()

    world.query(Equipment::class).each { id ->
      val equipment = get<Equipment>()
      for ((slot, worn) in equipment.getWorn()) {
        val live = worn.enchantments.filterNot { it.hasEnded(now) }
        if (live.size == worn.enchantments.size) continue

        equipment.replace(slot, worn.copy(enchantments = live))
        world.get(id, Inventory::class)?.mirrorEnchantments(worn.uniqueId, live)
        world.add(id, IsStatusValueDirty)
      }
    }
  }
}
