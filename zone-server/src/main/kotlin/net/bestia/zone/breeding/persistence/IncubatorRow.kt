package net.bestia.zone.breeding.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bestia.zone.breeding.EggSummary
import net.bestia.zone.breeding.Incubator
import java.time.Instant

/** An [Incubator] at rest, one row per incubator structure that ever held an egg. */
@Entity
@Table(name = "incubator")
class IncubatorRow(
  @Id
  @Column(name = "structure_id")
  val structureId: Long,

  @Column(name = "egg_instance_id") val eggInstanceId: Long?,
  @Column(name = "since") val since: Instant?,
  @Column(name = "reduction_percent", nullable = false) val reductionPercent: Int,
  @Column(name = "waiting_for_slot", nullable = false) val waitingForSlot: Boolean,
) {

  /** [egg] is the row [eggInstanceId] names, read by the caller. */
  fun toIncubator(egg: BestiaEgg?): Incubator {
    return Incubator(
      structureId = structureId,
      egg = egg?.let(EggSummary::of),
      since = since,
      reductionPercent = reductionPercent,
      waitingForSlot = waitingForSlot,
    )
  }

  companion object {
    fun of(incubator: Incubator): IncubatorRow {
      return IncubatorRow(
        structureId = incubator.structureId,
        eggInstanceId = incubator.egg?.instanceId,
        since = incubator.since,
        reductionPercent = incubator.reductionPercent,
        waitingForSlot = incubator.waitingForSlot,
      )
    }
  }
}
