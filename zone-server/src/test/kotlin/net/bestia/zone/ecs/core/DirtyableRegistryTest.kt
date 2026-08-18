package net.bestia.zone.ecs.core

import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.SkillPoints
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirtyableRegistryTest {

  @Test
  fun `discovers every known Dirtyable component type`() {
    val syncTypes = scanDirtyableComponentTypes()

    val expected = setOf(
      Position::class,
      Speed::class,
      Path::class,
      Health::class,
      Mana::class,
      Inventory::class,
      Exp::class,
      Level::class,
      EntityVisual::class,
      SkillPoints::class,
    )

    assertTrue(
      syncTypes.toSet().containsAll(expected),
      "expected all known Dirtyable types to be discovered, missing: ${expected - syncTypes.toSet()}"
    )
  }
}
