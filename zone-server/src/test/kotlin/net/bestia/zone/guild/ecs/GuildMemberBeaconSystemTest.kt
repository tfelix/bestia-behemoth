package net.bestia.zone.guild.ecs

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.PlaceNameService
import net.bestia.zone.place.ecs.PlaceRef
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildMemberBeaconSystemTest {

  private val placeNames = mockk<PlaceNameService> {
    every { resolve(any(), any()) } returns PlaceRef("Glimmerwood")
  }
  private val world = testWorld(systems = listOf(GuildMemberBeaconSystem(placeNames)))

  @Test
  fun `the beacon carries where the member is and how its master fares`() {
    val master = master(1, x = 10)

    pulse()

    val beacon = beaconOf(master)
    assertEquals(Vec3L(10, 0, 0), beacon.position)
    assertEquals("Glimmerwood", beacon.areaName)
    assertEquals(GuildMemberBeacon.Vitals(80, 100, 30, 50), beacon.vitals)
  }

  @Test
  fun `it follows the bestia the member plays`() {
    val master = master(1, x = 0, playing = false)
    world.createEntity { id ->
      add(id, Account(1))
      add(id, OwnedBestia(masterId = 1, playerBestiaId = 8L))
      add(id, ActivePlayer)
      add(id, Position(300, 40, 0))
    }

    pulse()

    assertEquals(Vec3L(300, 40, 0), beaconOf(master).position)
  }

  @Test
  fun `a few steps are no news, a few metres are`() {
    val master = master(1, x = 0)
    pulse()
    beaconOf(master).clearDirty()

    moveTo(master, 3)
    pulse()
    assertFalse(beaconOf(master).isDirty())

    moveTo(master, 4)
    pulse()
    assertTrue(beaconOf(master).isDirty())
  }

  @Test
  fun `a member coming online hears every beacon of the guild again`() {
    val standingStill = master(1, x = 0)
    val otherGuild = master(2, x = 0, guildId = OTHER_GUILD)
    pulse()
    beaconOf(standingStill).clearDirty()
    beaconOf(otherGuild).clearDirty()

    master(3, x = 50)
    pulse()

    assertTrue(beaconOf(standingStill).isDirty())
    assertFalse(beaconOf(otherGuild).isDirty())
  }

  private fun master(masterId: Long, x: Long, guildId: Long = GUILD, playing: Boolean = true): EntityId {
    return world.createEntity { id ->
      add(id, Account(masterId))
      add(id, Master(masterId))
      add(id, Position(x, 0, 0))
      add(id, Health(current = 80, max = 100))
      add(id, Mana(current = 30, max = 50))
      add(id, GuildMembership(guildId, taxPercent = 5))
      add(id, GuildMemberBeacon())
      if (playing) {
        add(id, ActivePlayer)
      }
    }
  }

  private fun moveTo(id: EntityId, x: Long) {
    world.modify(id) { get(it, Position::class)!!.x = x }
  }

  private fun beaconOf(id: EntityId): GuildMemberBeacon {
    return world.read { get(id, GuildMemberBeacon::class)!! }
  }

  private fun pulse() {
    repeat(2) { world.tick(1f) }
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
  }
}
