package net.bestia.zone.doomsday.withering

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.zone.doomsday.ForeshadowKind
import net.bestia.zone.doomsday.persistence.StoredCorruptionCrystal
import net.bestia.zone.doomsday.persistence.StoredCorruptionCrystalRepository
import net.bestia.zone.doomsday.persistence.StoredForeshadowing
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlightBloomTest {

  private val stored = mutableListOf<StoredCorruptionCrystal>()
  private var nextId = 1L
  private val rows = mockk<StoredCorruptionCrystalRepository>(relaxed = true) {
    every { findAll() } answers { stored.toList() }
    val saved = slot<StoredCorruptionCrystal>()
    every { save(capture(saved)) } answers {
      saved.captured.also {
        StoredCorruptionCrystal::class.java.getDeclaredField("id").apply { isAccessible = true }.setLong(it, nextId++)
        stored += it
      }
    }
    every { deleteAllById(any()) } answers { firstArg<Iterable<Long>>().forEach { id -> stored.removeIf { it.id == id } } }
    every { deleteAll(any<Iterable<StoredCorruptionCrystal>>()) } answers {
      firstArg<Iterable<StoredCorruptionCrystal>>().forEach { stored.remove(it) }
    }
  }
  private val broken = mutableListOf<Long>()
  private val crystals = mockk<CorruptionCrystals>(relaxed = true) {
    every { takeBroken() } answers { broken.toList().also { broken.clear() } }
  }
  private val field = CorruptionField()
  private val config = WitheringConfig()
  private val bloom = BlightBloom(
    config, field, rows, crystals, CorruptionSpread(config, field),
    mockk { every { wardedNow() } returns emptySet() },
    mockk(relaxed = true),
    mockk { every { isSanctuaryAt(any(), any()) } returns false },
    mockk { every { grounded(any(), any()) } returns Vec3L(0, 0, 0) },
  )
  private val season = StoredForeshadowing(
    kind = ForeshadowKind.BLIGHT_BLOOM, provinceIndex = 2, x = 20_000.0, y = 20_000.0, endsDay = 100.0,
  )

  @Test
  fun `it grows crystals around its heart and rots the land under them`() {
    bloom.step(season, DAY)

    assertEquals(5, stored.count { it.bloom })
    assertEquals(5, season.lost)
    assertTrue(field.intensityAt(22_500.0, 20_000.0) > 0.0)
  }

  @Test
  fun `broken crystals count as held, and contained, the land heals`() {
    bloom.step(season, DAY)
    broken += stored.take(3).map { it.id }
    bloom.step(season, DAY)

    assertEquals(3, season.held)
    assertEquals(2, season.lost)
    assertTrue(bloom.contained(season))

    bloom.end(season, contained = true)
    assertFalse(stored.any { it.bloom })
    assertEquals(0.0, field.intensityAt(22_500.0, 20_000.0))
  }

  private companion object {
    const val DAY = 86_400.0
  }
}
