package net.bestia.zone.ai.perception

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostilityTest {

  private val world = testWorld()

  private val wild = world.createEntity { }
  private val master = world.createEntity { world.add(it, Master(1L)) }
  private val bestia = ownedBestia(masterId = 1L)
  private val strangersBestia = ownedBestia(masterId = 2L)

  private fun ownedBestia(masterId: Long): EntityId {
    return world.createEntity { world.add(it, OwnedBestia(masterId = masterId, playerBestiaId = it)) }
  }

  @Test
  fun `the wild hunts masters and what they own`() {
    assertTrue(Hostility.isHostile(world, wild, master))
    assertTrue(Hostility.isHostile(world, wild, bestia))
  }

  @Test
  fun `a bestia sees masters as enemies but not other bestias`() {
    assertTrue(Hostility.isHostile(world, bestia, master))
    assertFalse(Hostility.isHostile(world, bestia, strangersBestia))
  }

  @Test
  fun `nobody hunts the wild on sight`() {
    assertFalse(Hostility.isHostile(world, bestia, wild))
    assertFalse(Hostility.isHostile(world, wild, wild))
  }
}
