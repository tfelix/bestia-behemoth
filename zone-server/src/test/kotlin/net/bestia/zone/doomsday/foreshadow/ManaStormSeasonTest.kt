package net.bestia.zone.doomsday.foreshadow

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.doomsday.ForeshadowKind
import net.bestia.zone.doomsday.persistence.StoredForeshadowing
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import net.bestia.zone.worldevent.rift.Rifts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManaStormSeasonTest {

  private val active = mutableListOf<StoredRift>()
  private val ended = HashMap<Long, Phase>()
  private val rifts = mockk<Rifts> {
    every { active() } answers { active.toList() }
    every { phaseOf(any()) } answers { active.firstOrNull { it.id == firstArg<Long>() }?.phase ?: ended[firstArg()] }
  }
  private val field = ManaField()
  private val world = mockk<WorldView>(relaxed = true)
  private val season = ManaStormSeason(ManaStormSeasonConfig(), field, rifts, world, mockk(relaxed = true))
  private val running = StoredForeshadowing(
    kind = ForeshadowKind.MANA_STORM_SEASON, provinceIndex = 4, x = 10_000.0, y = 10_000.0, endsDay = 100.0,
  )

  @Test
  fun `it floods its province with mana`() {
    season.step(running, 86_400.0)

    assertTrue(field.deltaAt(10_000.0, 10_000.0) > 0.05)
  }

  @Test
  fun `the province's sealed rifts count as held, its collapsed ones as lost`() {
    listOf(1L, 2L, 3L).forEach { id ->
      active += StoredRift(x = 10_000.0, y = 10_000.0, provinceIndex = 4, formedAtSecond = 0, opensAtSecond = 0)
        .also { it.id = id }
    }
    active += StoredRift(x = 0.0, y = 0.0, provinceIndex = 9, formedAtSecond = 0, opensAtSecond = 0).also { it.id = 9 }
    season.step(running, 300.0)

    active.clear()
    ended[1] = Phase.SEALED
    ended[2] = Phase.DISSOLVED
    ended[3] = Phase.COLLAPSED
    ended[9] = Phase.COLLAPSED
    season.step(running, 300.0)

    assertEquals(2, running.held)
    assertEquals(1, running.lost)
    assertTrue(season.contained(running))
  }

  @Test
  fun `run its course, it scars the land`() {
    season.end(running, contained = false)

    verify { world.post(any()) }
  }
}
