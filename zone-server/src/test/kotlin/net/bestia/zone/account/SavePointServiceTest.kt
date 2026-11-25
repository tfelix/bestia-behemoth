package net.bestia.zone.account

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.SpawnPointAvailability
import org.junit.jupiter.api.Test
import java.awt.Color
import java.util.Optional
import kotlin.test.assertEquals

class SavePointServiceTest {

  private val masterId = 5L
  private val playerBestiaId = 7L

  private val nearTown = Vec3L(100, 100, 10)
  private val farTown = Vec3L(5000, 5000, 10)

  private val master = Master(
    account = mockk(relaxed = true),
    slot = 0,
    name = "Tester",
    hairColor = Color.BLACK,
    skinColor = Color.WHITE,
    hair = Hairstyle.entries.first(),
    face = Face.entries.first(),
    body = BodyType.entries.first(),
  ).also { it.currentPosition = Vec3L(150, 90, 12) }

  private val playerBestia = PlayerBestia(master = master, bestia = mockk(relaxed = true))

  private val masters = mockk<MasterRepository> {
    every { findById(masterId) } returns Optional.of(master)
  }
  private val playerBestias = mockk<PlayerBestiaRepository> {
    every { findById(playerBestiaId) } returns Optional.of(playerBestia)
  }

  private var offered = listOf(startTown("Far Town", farTown), startTown("Near Town", nearTown))
  private val startTowns = mockk<SpawnPointAvailability> {
    every { offered() } answers { offered }
  }

  private val sut = SavePointService(masters, playerBestias, startTowns)

  private fun startTown(name: String, position: Vec3L): SpawnPointAvailability.Offer {
    return SpawnPointAvailability.Offer(1, name, "town", position, settlementIndex = 0, population = 100)
  }

  @Test
  fun `a master keeps the save point it has`() {
    master.spawnPosition = farTown
    master.homeSettlementName = "Far Town"

    assertEquals(farTown, sut.forMaster(masterId))
    assertEquals("Far Town", master.homeSettlementName)
  }

  @Test
  fun `a master without a save point gets the start town nearest to where it stands`() {
    assertEquals(nearTown, sut.forMaster(masterId))

    assertEquals(nearTown, master.spawnPosition)
    assertEquals("Near Town", master.homeSettlementName)
  }

  @Test
  fun `a master stays without a save point when the world offers no start town`() {
    offered = emptyList()

    assertEquals(Vec3L.ZERO, sut.forMaster(masterId))
    assertEquals("", master.homeSettlementName)
  }

  @Test
  fun `a bestia keeps its own save point`() {
    playerBestia.spawnPosition = Vec3L(1, 2, 3)

    assertEquals(Vec3L(1, 2, 3), sut.forPlayerBestia(playerBestiaId))
    assertEquals(Vec3L.ZERO, master.spawnPosition)
  }

  @Test
  fun `a bestia without a save point uses its master's`() {
    master.spawnPosition = farTown

    assertEquals(farTown, sut.forPlayerBestia(playerBestiaId))
  }

  @Test
  fun `a bestia whose master has no save point either gives its master the nearest start town`() {
    assertEquals(nearTown, sut.forPlayerBestia(playerBestiaId))

    assertEquals(nearTown, master.spawnPosition)
    assertEquals("Near Town", master.homeSettlementName)
  }
}
