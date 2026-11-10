package net.bestia.zone.breeding.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bestia.zone.breeding.BreedingFarm
import net.bestia.zone.breeding.BreedingPair
import net.bestia.zone.breeding.EggSummary
import net.bestia.zone.breeding.FarmPhase
import java.time.Duration
import java.time.Instant

/** A [BreedingFarm] at rest, one row per farm structure that ever held anything. */
@Entity
@Table(name = "breeding_farm")
class BreedingFarmRow(
  @Id
  @Column(name = "structure_id")
  val structureId: Long,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val phase: FarmPhase,

  @Column(name = "mother_bestia_id") val motherBestiaId: Long?,
  @Column(name = "mother_entity_id") val motherEntityId: Long?,
  @Column(name = "mother_level") val motherLevel: Int?,
  @Column(name = "father_bestia_id") val fatherBestiaId: Long?,
  @Column(name = "father_entity_id") val fatherEntityId: Long?,
  @Column(name = "father_level") val fatherLevel: Int?,

  @Column(name = "has_bundle", nullable = false) val hasBundle: Boolean,
  @Column(name = "clutch_millis", nullable = false) val clutchMillis: Long,
  @Column(name = "ends_at") val endsAt: Instant?,
  @Column(name = "dominant_ivs", nullable = false) val dominantIvs: Int,
  @Column(name = "beastfriend_level", nullable = false) val beastfriendLevel: Int,
  @Column(name = "egg_instance_id") val eggInstanceId: Long?,
) {

  /** [egg] is the row [eggInstanceId] names, read by the caller. */
  fun toFarm(egg: BestiaEgg?): BreedingFarm {
    val motherId = motherBestiaId
    val fatherId = fatherBestiaId
    val pair = if (motherId != null && fatherId != null) {
      BreedingPair(
        motherId, checkNotNull(motherEntityId), checkNotNull(motherLevel),
        fatherId, checkNotNull(fatherEntityId), checkNotNull(fatherLevel),
      )
    } else {
      null
    }

    return BreedingFarm(
      structureId = structureId,
      // A roll that was cut off by a restart is rolled again.
      phase = if (phase == FarmPhase.LAYING) FarmPhase.BREEDING else phase,
      pair = pair,
      hasBundle = hasBundle,
      clutchTime = Duration.ofMillis(clutchMillis),
      endsAt = endsAt,
      dominantIvs = dominantIvs,
      beastfriendLevel = beastfriendLevel,
      egg = egg?.let(EggSummary::of),
    )
  }

  companion object {
    fun of(farm: BreedingFarm): BreedingFarmRow {
      val pair = farm.pair
      return BreedingFarmRow(
        structureId = farm.structureId,
        phase = farm.phase,
        motherBestiaId = pair?.motherBestiaId,
        motherEntityId = pair?.motherEntityId,
        motherLevel = pair?.motherLevel,
        fatherBestiaId = pair?.fatherBestiaId,
        fatherEntityId = pair?.fatherEntityId,
        fatherLevel = pair?.fatherLevel,
        hasBundle = farm.hasBundle,
        clutchMillis = farm.clutchTime.toMillis(),
        endsAt = farm.endsAt,
        dominantIvs = farm.dominantIvs,
        beastfriendLevel = farm.beastfriendLevel,
        eggInstanceId = farm.egg?.instanceId,
      )
    }
  }
}
