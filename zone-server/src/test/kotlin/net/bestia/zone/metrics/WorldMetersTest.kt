package net.bestia.zone.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.InCombat
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.Living
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WorldMetersTest {

  private val world = testWorld()
  private val meters = SimpleMeterRegistry()

  private fun entities(kind: String): Double {
    return meters.get("zone.world.entities").tag("kind", kind).gauge().value()
  }

  @Test
  fun `a mob is a creature that is neither owned nor a townsperson`() {
    WorldMeters(world).bindTo(meters)

    world.createEntity { id -> add(id, Living) }
    world.createEntity { id -> add(id, Living) }
    world.createEntity { id ->
      add(id, Living)
      add(id, Account(1L))
    }

    assertEquals(2.0, entities("mob"))
    assertEquals(0.0, entities("townsfolk"))
  }

  @Test
  fun `a kind nobody has made yet reads zero`() {
    WorldMeters(world).bindTo(meters)

    assertEquals(0.0, entities("ground_item"))
    assertEquals(0.0, entities("master"))
  }

  @Test
  fun `a corpse is counted while it lies there`() {
    WorldMeters(world).bindTo(meters)

    val body = world.createEntity { id -> add(id, Dead()) }
    assertEquals(1.0, entities("corpse"))

    world.destroy(body)
    assertEquals(0.0, entities("corpse"))
  }

  @Test
  fun `entities created and destroyed are counted`() {
    WorldMeters(world).bindTo(meters)

    val first = world.createEntity { }
    world.createEntity { }
    world.destroy(first)

    assertEquals(2.0, meters.get("zone.world.entities.lifecycle").tag("event", "created").functionCounter().count())
    assertEquals(1.0, meters.get("zone.world.entities.lifecycle").tag("event", "destroyed").functionCounter().count())
  }

  @Test
  fun `the sampler counts what masters are doing and the effects that run`() {
    val sampler = WorldStateSampler(meters)
    val sampled = testWorld(systems = listOf(sampler))

    sampled.createEntity { id ->
      add(id, Master(1L))
      add(id, InCombat())
    }
    sampled.createEntity { id -> add(id, Master(2L)) }
    sampled.createEntity { id -> add(id, InCombat()) } // not a master: a creature in a fight
    sampled.createEntity { id ->
      add(id, StatusEffects(mutableListOf(ActiveStatusEffect(definitionId = 7L, level = 1, remainingSeconds = 10f))))
    }

    sampled.tick(5f)

    assertEquals(1.0, meters.get("zone.players.active").tag("activity", "in_combat").gauge().value())
    assertEquals(0.0, meters.get("zone.players.active").tag("activity", "casting").gauge().value())
    assertEquals(1.0, meters.get("zone.status.effects.active").tag("effect", "7").gauge().value())
  }
}
