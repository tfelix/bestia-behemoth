package net.bestia.zone.faction.ecs

import net.bestia.worldgen.core.Faction
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FactionBadgeSystemTest {

  private val world = testWorld(systems = listOf(FactionBadgeSystem()))

  private val sworn = FactionMembership(Faction.CHAOS, swornAt = Instant.EPOCH)

  @Test
  fun `a sworn master and its bestia carry the badge of its Order`() {
    val master = world.createEntity { id ->
      add(id, Master(1))
      add(id, sworn)
    }
    val bestia = world.createEntity { id -> add(id, OwnedBestia(masterId = 1, playerBestiaId = 3L)) }

    pulse()

    assertEquals(FactionBadge(Faction.CHAOS), world.read { get(master, FactionBadge::class) })
    assertEquals(FactionBadge(Faction.CHAOS), world.read { get(bestia, FactionBadge::class) })
  }

  @Test
  fun `an unsworn master carries no badge, and leaving takes it away`() {
    val master = world.createEntity { id ->
      add(id, Master(1))
      add(id, FactionMembership())
    }
    pulse()
    assertNull(world.read { get(master, FactionBadge::class) })

    world.modify(master) { add(it, sworn) }
    pulse()
    assertEquals(Faction.CHAOS, world.read { get(master, FactionBadge::class)?.faction })

    world.modify(master) { add(it, FactionMembership(unswornUntil = Instant.EPOCH)) }
    pulse()
    assertNull(world.read { get(master, FactionBadge::class) })
  }

  private fun pulse() {
    repeat(2) { world.tick(1f) }
  }
}
