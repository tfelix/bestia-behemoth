package net.bestia.zone.doomsday

import net.bestia.worldgen.vector.Vec2d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DoomsdayPickerTest {

  private class FakeThreat(
    override val kind: DoomKind,
    private val fits: Boolean = true,
  ) : DoomThreat {
    override val shape = DoomShape.ACTOR
    override fun fits(): Boolean {
      return fits
    }

    override fun origin(random: Random): Vec2d {
      return Vec2d(kind.ordinal * 1000.0, 7.0)
    }
  }

  private val threats = listOf(
    FakeThreat(DoomKind.VOID_TEAR),
    FakeThreat(DoomKind.WITHERING),
    FakeThreat(DoomKind.FLOOD, fits = false),
  )

  private val foreshadows = listOf(ForeshadowKind.MANA_STORM_SEASON, ForeshadowKind.BLIGHT_BLOOM)

  @Test
  fun `the same world always gets the same fate`() {
    val picker = DoomsdayPicker(DoomsdayConfig(), threats, foreshadows)
    val first = picker.pick(SEED)
    val again = picker.pick(SEED)

    assertEquals(first.kind, again.kind)
    assertEquals(first.onsetDay, again.onsetDay)
    assertEquals(first.unfoldDays, again.unfoldDays)
    assertEquals(first.foreshadowKind, again.foreshadowKind)
  }

  @Test
  fun `times stay within the configured windows`() {
    val config = DoomsdayConfig()
    val picker = DoomsdayPicker(config, threats, foreshadows)

    for (seed in 1L..200L) {
      val fate = picker.pick(seed)
      assertTrue(fate.onsetDay in 180.0..270.0, "onset ${fate.onsetDay}")
      assertTrue(fate.unfoldDays in 180.0..365.0, "unfold ${fate.unfoldDays}")
      assertTrue(fate.foreshadowDay in fate.onsetDay * 0.4..fate.onsetDay * 0.6, "foreshadow ${fate.foreshadowDay}")
    }
  }

  @Test
  fun `only a threat that fits the world is drawn`() {
    val picker = DoomsdayPicker(DoomsdayConfig(), threats, foreshadows)
    val drawn = (1L..200L).map { picker.pick(it).kind }.toSet()

    assertEquals(setOf(DoomKind.VOID_TEAR, DoomKind.WITHERING), drawn)
  }

  @Test
  fun `a forced threat is taken with its forced origin`() {
    val config = DoomsdayConfig(forceKind = DoomKind.WITHERING, forceOriginX = 5.0, forceOriginY = 6.0)
    val fate = DoomsdayPicker(config, threats, foreshadows).pick(SEED)

    assertEquals(DoomKind.WITHERING, fate.kind)
    assertEquals(5.0, fate.originX)
    assertEquals(6.0, fate.originY)
  }

  @Test
  fun `a forced threat that does not fit falls back to a drawn one`() {
    val fate = DoomsdayPicker(DoomsdayConfig(forceKind = DoomKind.FLOOD), threats, foreshadows).pick(SEED)

    assertTrue(fate.kind in setOf(DoomKind.VOID_TEAR, DoomKind.WITHERING), "drawn instead: ${fate.kind}")
  }

  @Test
  fun `the Foreshadowing comes only from built ones and can be forced`() {
    val picker = DoomsdayPicker(DoomsdayConfig(), threats, foreshadows)
    val drawn = (1L..200L).map { picker.pick(it).foreshadowKind }.toSet()
    assertEquals(setOf(ForeshadowKind.MANA_STORM_SEASON, ForeshadowKind.BLIGHT_BLOOM), drawn)

    val forcing = DoomsdayConfig(forceForeshadow = ForeshadowKind.BLIGHT_BLOOM)
    val forced = DoomsdayPicker(forcing, threats, foreshadows).pick(SEED)
    assertEquals(ForeshadowKind.BLIGHT_BLOOM, forced.foreshadowKind)
  }

  @Test
  fun `a world no threat fits still gets a clock`() {
    val fate = DoomsdayPicker(DoomsdayConfig(), emptyList()).pick(SEED)

    assertNull(fate.kind)
    assertNull(fate.foreshadowKind)
    assertTrue(fate.lifeSpanDays > 360.0)
  }

  private companion object {
    const val SEED = 11_753_242L
  }
}
