package net.bestia.zone.sync

import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Speed
import net.bestia.zone.battle.ecs.exp.Exp
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.SkillPoints
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

  @Test
  fun `reports only types that can actually sit in a store`() {
    val abstract = scanDirtyableComponentTypes().filter { java.lang.reflect.Modifier.isAbstract(it.java.modifiers) }

    assertTrue(abstract.isEmpty(), "abstract types would get an empty store each: $abstract")
  }
}
