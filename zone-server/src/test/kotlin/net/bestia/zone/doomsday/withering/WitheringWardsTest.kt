package net.bestia.zone.doomsday.withering

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.doomsday.persistence.StoredWardedCell
import net.bestia.zone.doomsday.persistence.StoredWardedCellRepository
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.mana.ManaField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WitheringWardsTest {

  private val config = WitheringConfig()
  private val field = CorruptionField()
  private val stored = mutableListOf<StoredWardedCell>()
  private val repository = mockk<StoredWardedCellRepository> {
    val saved = slot<Iterable<StoredWardedCell>>()
    every { saveAll(capture(saved)) } answers { saved.captured.toList().also { stored += it } }
    every { findAll() } answers { stored.toList() }
  }
  private val wards = WitheringWards(config, field, repository)
  private val spread = CorruptionSpread(config, field)
  private val cell = ManaField.Cell(ManaField.cellOf(5_000.0), ManaField.cellOf(5_000.0))

  @Test
  fun `a ritual on rotten land cleanses it and keeps the Withering off for a week`() {
    field.change(cell, 0.9)

    wards.onRitualCompleted(mockk(), Vec3L(5_000, 5_000, 0), emptyList())
    assertEquals(0.3, field.intensityOf(cell), 1e-9)

    spread.corrupt(listOf(Vec2d(5_000.0, 5_000.0)), 1.0, wards.wardedNow(), heal = true)
    assertEquals(0.3, field.intensityOf(cell), 1e-9, "the warded cell does not rot again")
  }

  @Test
  fun `a ritual on clean land wards nothing`() {
    wards.onRitualCompleted(mockk(), Vec3L(5_000, 5_000, 0), emptyList())

    assertTrue(wards.wardedNow().isEmpty())
  }

  @Test
  fun `a kill on rotten land eases the rot`() {
    field.change(cell, 0.9)
    val kills = CorruptedKills(config, field)

    kills.onKill(mockk(), KillFact(1L, Vec3L(5_000, 5_000, 0), setOf(7L), 10, false, null as EventCreature?))

    assertEquals(0.87, field.intensityOf(cell), 1e-9)
  }
}
