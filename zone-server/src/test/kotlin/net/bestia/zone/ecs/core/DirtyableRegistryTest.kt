package net.bestia.zone.ecs.core

import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.battle.Mana
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.ItemVisual
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.status.Exp
import net.bestia.zone.ecs.status.Level
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirtyableRegistryTest {

  @Test
  fun `discovers every known Dirtyable component type`() {
    val syncTypes = DirtyableRegistry().syncTypes

    val expected = setOf(
      Position::class,
      Speed::class,
      Path::class,
      Health::class,
      Mana::class,
      Inventory::class,
      ItemVisual::class,
      Exp::class,
      Level::class,
      BestiaVisual::class,
    )

    assertTrue(
      syncTypes.toSet().containsAll(expected),
      "expected all known Dirtyable types to be discovered, missing: ${expected - syncTypes.toSet()}"
    )
  }
}
