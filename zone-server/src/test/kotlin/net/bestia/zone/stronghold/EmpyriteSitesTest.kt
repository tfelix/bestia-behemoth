package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.stronghold.persistence.StrongholdRecord
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmpyriteSitesTest {

  private val world = testWorld()
  private val now = Instant.parse("2026-11-21T12:00:00Z")
  private val seed = EmpyriteSeed(rank = 2, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private val held = mutableListOf<Stronghold>()
  private val entries = mutableMapOf<Long, StructureEntry>()
  private val sprouts = mockk<EmpyriteSeedSprouts>(relaxed = true)
  private val news = mockk<StrongholdNews>(relaxed = true)

  private val strongholds = mockk<StrongholdRegistry> {
    every { all() } answers { held.toList() }
    every { put(any()) } answers { held.removeIf { it.rank == firstArg<Stronghold>().rank }; held.add(firstArg()) }
    every { remove(any()) } answers { held.removeIf { it.rank == firstArg<Int>() } }
  }

  private val structures = mockk<PlayerStructureRegistry> {
    every { of(any()) } answers { entries[firstArg()] }
    every { remove(any(), any(), any()) } answers { entries.remove(firstArg()) }
  }

  private val sites = EmpyriteSites(
    strongholds = strongholds,
    seeds = mockk { every { awake() } returns listOf(seed) },
    sprouts = sprouts,
    structures = structures,
    worldService = mockk<WorldService> { every { config } returns WorldConfig(seed = 1L, chunkSize = 32) },
    news = news,
    config = StrongholdConfig(siteIdleHours = 24),
    clock = Clock.fixed(now, ZoneOffset.UTC),
  )

  @Test
  fun `a site somebody works on stays, and counts as worked now`() {
    val site = siteWorkedAt(now.minusSeconds(30 * 3600), active = true)

    sites.expire(world)

    assertEquals(now, held.single().siteWorkedAt)
    assertTrue(world.isAlive(site))
  }

  @Test
  fun `a site left alone for a day is gone, and its seed sprouts again`() {
    val site = siteWorkedAt(now.minusSeconds(25 * 3600), active = false)

    sites.expire(world)

    assertTrue(held.isEmpty())
    assertTrue(!world.isAlive(site))
    assertTrue(entries.isEmpty())
    verify { sprouts.raise(world, seed) }
    verify { news.lost(world, GUILD, seed.rank) }
  }

  @Test
  fun `a destroyed site frees its seed at once`() {
    siteWorkedAt(now, active = false)
    entries.clear()

    sites.expire(world)

    assertTrue(held.isEmpty())
    verify { sprouts.raise(world, seed) }
  }

  @Test
  fun `a restored site belongs to its guild again`() {
    val site = siteWorkedAt(now, active = false)

    sites.restoreOwners(world)

    assertEquals(GuildOwned(GUILD), world.get(site, GuildOwned::class))
  }

  private fun siteWorkedAt(workedAt: Instant, active: Boolean): Long {
    val entry = StructureEntry(
      STRUCTURE_ID, StaticEntityKind.EMPYRITE_SITE, ownerMasterId = 5L, ownerAccountId = 7L,
      position = Vec3L(1_000, 1_000, 0), yaw = 0f, totalBuildSeconds = 1800f, remainingBuildSeconds = 900f
    )
    entries[STRUCTURE_ID] = entry
    held.add(Stronghold(seed.rank, GUILD, StrongholdRecord.State.SITE, STRUCTURE_ID, workedAt, workedAt))

    return world.createEntity { id ->
      add(id, ConstructionSite(StaticEntityKind.EMPYRITE_SITE, 5L, STRUCTURE_ID, 0f, 1800f, 900f).also { it.active = active })
    }
  }

  private companion object {
    const val GUILD = 1L
    const val STRUCTURE_ID = 40L
  }
}
