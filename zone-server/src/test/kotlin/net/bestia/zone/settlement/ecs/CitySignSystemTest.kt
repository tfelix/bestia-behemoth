package net.bestia.zone.settlement.ecs

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.settlement.TownService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CitySignSystemTest {

  private val world = testWorld()
  private val towns = mockk<TownService>(relaxed = true)
  private val system = CitySignSystem(towns)

  @Test
  fun `a sign heals one percent a minute once unhit for five minutes`() {
    val sign = world.createEntity { id ->
      add(id, Health(current = 1_000, max = 2_000))
      add(id, CitySign(townId = 1L))
    }

    system.update(world, 299f)
    assertEquals(1_000, world.get(sign, Health::class)?.current, "not yet five minutes")

    system.update(world, 60f)
    assertEquals(1_020, world.get(sign, Health::class)?.current)
  }

  @Test
  fun `a hit restarts the wait`() {
    val sign = world.createEntity { id ->
      add(id, Health(current = 1_000, max = 2_000))
      add(id, CitySign(townId = 1L))
    }
    system.update(world, 299f)

    world.get(sign, Health::class)!!.current = 900
    system.update(world, 60f)

    assertEquals(900, world.get(sign, Health::class)?.current)
  }

  @Test
  fun `a destroyed sign ends its town`() {
    world.createEntity { id ->
      add(id, Health(current = 0, max = 2_000))
      add(id, CitySign(townId = 5L))
      add(id, Dead())
    }

    system.update(world, 0.05f)

    verify { towns.fall(5L) }
  }
}
