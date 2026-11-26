package net.bestia.zone.worldevent.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** One rift: where it is, how far it has come and how much mana it holds. Times are Bestia seconds. */
@Entity
@Table(name = "rift")
class StoredRift(
  /** Its centre, in metres. */
  @Column(nullable = false)
  var x: Double = 0.0,

  @Column(nullable = false)
  var y: Double = 0.0,

  @Column(name = "province_index", nullable = false)
  var provinceIndex: Int = 0,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var phase: Phase = Phase.KNOT,

  /** 0 while a knot, then 1 to 5 while open. */
  @Column(nullable = false)
  var stage: Int = 0,

  /** The mana it has pulled in, in motes. */
  @Column(nullable = false)
  var reservoir: Double = 0.0,

  @Column(name = "formed_at_second", nullable = false)
  var formedAtSecond: Long = 0,

  @Column(name = "opens_at_second", nullable = false)
  var opensAtSecond: Long = 0,

  @Column(name = "collapses_at_second", nullable = false)
  var collapsesAtSecond: Long = 0,

  /** The anchor bestia an open rift calls at stage 3, while it stands; null before and after. */
  @Column(name = "anchor_entity_id")
  var anchorEntityId: Long? = null,

  /** Whether the anchor fell. A rift whose anchor stands cannot be sealed. */
  @Column(name = "anchor_slain", nullable = false)
  var anchorSlain: Boolean = false,

  /** The stage at which the next anchor steps out. Moves on when a seal window passes unused. */
  @Column(name = "next_anchor_stage", nullable = false)
  var nextAnchorStage: Int = 3,

  /** When the open seal window closes, or 0 while none is open. */
  @Column(name = "seal_window_ends_at_second", nullable = false)
  var sealWindowEndsAtSecond: Long = 0,

  /** When the next wave comes out of the gate. */
  @Column(name = "next_wave_at_second", nullable = false)
  var nextWaveAtSecond: Long = 0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,

  /** The species that anchors it, by identifier. Null for the Rift Warden every rift calls. */
  @Column(name = "anchor_species", length = 64)
  var anchorSpecies: String? = null,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long = 0

  /** A knot pulls quietly; an open rift pulls hard and grows. The rest are endings. */
  enum class Phase {
    KNOT, OPEN, SEALED, COLLAPSED, DISSOLVED, MERGED;

    val isActive: Boolean get() = this == KNOT || this == OPEN
  }
}
