package net.bestia.zone.doomsday.foreshadow

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.place.Province
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.doomsday.DoomStageNotices
import net.bestia.zone.doomsday.DoomsdayConfig
import net.bestia.zone.doomsday.ForeshadowKind
import net.bestia.zone.doomsday.persistence.StoredDoomsday
import net.bestia.zone.doomsday.persistence.StoredForeshadowing
import net.bestia.zone.doomsday.persistence.StoredForeshadowingRepository
import net.bestia.zone.place.ecs.ProvinceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.Optional
import java.util.stream.Stream

class ForeshadowRunnerTest {

  private class CountingForeshadowing : Foreshadowing {
    override val kind = ForeshadowKind.MANA_STORM_SEASON
    var held = 0
    var lost = 0
    var ended: Boolean? = null

    override fun step(season: StoredForeshadowing, realSeconds: Double) {
      season.held = held
      season.lost = lost
    }

    override fun end(season: StoredForeshadowing, contained: Boolean) {
      ended = contained
    }
  }

  private val foreshadowing = CountingForeshadowing()
  private var stored: StoredForeshadowing? = null
  private val seasons = mockk<StoredForeshadowingRepository>(relaxed = true) {
    every { findById(StoredForeshadowing.ONLY) } answers { Optional.ofNullable(stored) }
    val saved = slot<StoredForeshadowing>()
    every { save(capture(saved)) } answers { saved.captured.also { stored = it } }
  }
  private val sanctuary = provinceAt(0, 100.0)
  private val wilds = provinceAt(1, 9_000.0)
  private val provinces = mockk<ProvinceService> {
    every { provinces } returns mockk { every { provinces } returns listOf(sanctuary, wilds) }
    every { isSanctuary(sanctuary) } returns true
    every { isSanctuary(wilds) } returns false
  }
  private val notices = mockk<DoomStageNotices>(relaxed = true)
  private val runner = ForeshadowRunner(
    DoomsdayConfig(),
    seasons,
    provinces,
    notices,
    mockk<ObjectProvider<Foreshadowing>> { every { orderedStream() } answers { Stream.of(foreshadowing) } },
  )

  private val fate = StoredDoomsday(
    onsetDay = 200.0, unfoldDays = 200.0, foreshadowKind = ForeshadowKind.MANA_STORM_SEASON, foreshadowDay = 90.0,
  )

  @Test
  fun `it strikes a province outside the sanctuaries on its day`() {
    assertEquals(0.0, runner.run(fate, 80.0, 300.0, SEED))
    assertNull(stored)

    runner.run(fate, 90.0, 300.0, SEED)

    assertEquals(1, stored!!.provinceIndex)
    assertEquals(9_000.0, stored!!.x)
    assertTrue(stored!!.endsDay in 97.0..104.0)
    verify { notices.foreshadowBegan(ForeshadowKind.MANA_STORM_SEASON, 9_000.0, 9_000.0) }
  }

  @Test
  fun `held back, it moves the onset later, and run its course, sooner`() {
    runner.run(fate, 90.0, 300.0, SEED)
    foreshadowing.held = 2
    foreshadowing.lost = 1

    assertEquals(14.0, runner.run(fate, 120.0, 300.0, SEED))
    assertEquals(true, foreshadowing.ended)
    assertEquals(0.0, runner.run(fate, 121.0, 300.0, SEED), "it strikes only once")

    stored = null
    runner.run(fate, 90.0, 300.0, SEED)
    foreshadowing.lost = 3
    assertEquals(-14.0, runner.run(fate, 120.0, 300.0, SEED))
    verify { notices.foreshadowEnded(ForeshadowKind.MANA_STORM_SEASON, false) }
  }

  @Test
  fun `a GM ends it as they say`() {
    assertNull(runner.end(contained = true))
    runner.run(fate, 90.0, 300.0, SEED)

    assertEquals(-14.0, runner.end(contained = false))
    assertEquals(false, stored!!.contained)
  }

  private fun provinceAt(index: Int, at: Double): Province {
    return Province(index, "P$index", Province.Seat(Province.SeatKind.TOWN, Vec2d(at, at), 0), listOf(0))
  }

  private companion object {
    const val SEED = 42L
  }
}
