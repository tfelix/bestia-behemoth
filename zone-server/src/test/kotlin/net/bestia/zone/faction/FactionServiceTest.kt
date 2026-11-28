package net.bestia.zone.faction

import net.bestia.worldgen.core.Faction
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FactionServiceTest {

  private val now = Instant.parse("2026-11-28T12:00:00Z")
  private var clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
  private val world = testWorld()

  private fun service(): FactionService {
    return FactionService(FactionConfig(minSwearLevel = 40), clock)
  }

  private fun World.master(level: Int): EntityId {
    return createEntity { id -> add(id, Level(level)) }
  }

  @Test
  fun `a master of the minimum level swears to an Order`() {
    val master = world.master(level = 40)

    assertNull(service().swear(world, master, Faction.CIRCLE))

    assertEquals(Faction.CIRCLE, service().factionOf(world, master))
    assertEquals(now, world.get(master, FactionMembership::class)?.swornAt)
  }

  @Test
  fun `a master below the minimum level is refused`() {
    val master = world.master(level = 39)

    assertEquals(FactionService.Refusal.LEVEL_TOO_LOW, service().swear(world, master, Faction.CHAOS))
    assertNull(service().factionOf(world, master))
  }

  @Test
  fun `a sworn master cannot swear to a second Order`() {
    val master = world.master(level = 50)
    service().swear(world, master, Faction.CHAOS)

    assertEquals(FactionService.Refusal.ALREADY_SWORN, service().swear(world, master, Faction.ETERNITY))
    assertEquals(Faction.CHAOS, service().factionOf(world, master))
  }

  @Test
  fun `a master who left waits out the time unsworn before swearing again`() {
    val master = world.master(level = 50)
    service().swear(world, master, Faction.CHAOS)

    assertTrue(service().leave(world, master, Duration.ofHours(200)))
    assertNull(service().factionOf(world, master))
    assertEquals(FactionService.Refusal.STILL_UNSWORN, service().swear(world, master, Faction.CHAOS))

    clock = Clock.fixed(now.plus(Duration.ofHours(200)), ZoneOffset.UTC)
    assertNull(service().swear(world, master, Faction.ETERNITY))
  }

  @Test
  fun `an unsworn master cannot leave`() {
    val master = world.master(level = 50)

    assertFalse(service().leave(world, master, Duration.ZERO))
  }
}
