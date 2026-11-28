package net.bestia.zone.faction.warzone

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.faction.FactionConfig
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarZonesTest {

  private var now = Instant.parse("2026-11-28T12:00:00Z")
  private val clock = mockk<Clock> { every { instant() } answers { now } }
  private val worldService = mockk<WorldService> { every { config.voxelSize } returns 1.0 }
  private val config = FactionConfig(
    warZone = FactionConfig.WarZone(radiusMetres = 1000.0, lingerMinutes = 30, kills = 3, killWindowMinutes = 10, killAreaMetres = 300.0)
  )
  private val warZones = WarZones(config, worldService, clock)

  @Test
  fun `three rival kills close together open a zone around them`() {
    warZones.rivalKill(Vec3L(0, 0, 0))
    warZones.rivalKill(Vec3L(100, 0, 0))
    assertTrue(warZones.open.isEmpty())

    warZones.rivalKill(Vec3L(200, 0, 0))

    assertEquals(Vec3L(100, 0, 0), warZones.open.single().centre)
    assertTrue(warZones.covers(Vec3L(1_100, 0, 0)))
    assertFalse(warZones.covers(Vec3L(1_200, 0, 0)))
  }

  @Test
  fun `kills too far apart or too long ago open nothing`() {
    warZones.rivalKill(Vec3L(0, 0, 0))
    warZones.rivalKill(Vec3L(5_000, 0, 0))
    now = now.plus(Duration.ofMinutes(11))
    warZones.rivalKill(Vec3L(10, 0, 0))
    warZones.rivalKill(Vec3L(5_010, 0, 0))

    assertTrue(warZones.open.isEmpty())
  }

  @Test
  fun `a zone closes half an hour after its last kill`() {
    openZoneAt(Vec3L(0, 0, 0))
    now = now.plus(Duration.ofMinutes(20))
    warZones.rivalKill(Vec3L(500, 0, 0))

    now = now.plus(Duration.ofMinutes(29))
    assertTrue(warZones.covers(Vec3L(0, 0, 0)))

    now = now.plus(Duration.ofMinutes(2))
    val before = warZones.version
    warZones.closeExpired()
    assertTrue(warZones.open.isEmpty())
    assertEquals(before + 1, warZones.version)
  }

  @Test
  fun `every master is sent the zones once, and again when they change`() {
    val out = mockk<OutMessageProcessor>(relaxed = true)
    val world = testWorld(systems = listOf(WarZoneSystem(warZones, out)))
    world.createEntity { id ->
      add(id, Account(accountId = ACCOUNT_ID))
      add(id, Master(masterId = 1L))
    }

    world.tick(5f)
    world.tick(5f)
    verify(exactly = 1) { out.sendToPlayer(ACCOUNT_ID, WarZonesSMSG(emptyList())) }

    openZoneAt(Vec3L(0, 0, 0))
    world.tick(5f)
    verify(exactly = 1) { out.sendToPlayer(ACCOUNT_ID, WarZonesSMSG(listOf(WarZonesSMSG.Circle(0.0, 0.0, 1000.0)))) }
  }

  private fun openZoneAt(at: Vec3L) {
    repeat(3) { warZones.rivalKill(at) }
  }

  private companion object {
    const val ACCOUNT_ID = 3L
  }
}
