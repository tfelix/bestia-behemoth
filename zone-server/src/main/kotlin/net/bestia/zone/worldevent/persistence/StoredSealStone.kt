package net.bestia.zone.worldevent.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A seal stone a sealed rift left, and the mana it holds, in motes. */
@Entity
@Table(name = "seal_stone")
class StoredSealStone(
  @Column(nullable = false)
  var x: Long = 0,

  @Column(nullable = false)
  var y: Long = 0,

  @Column(nullable = false)
  var z: Long = 0,

  @Column(nullable = false)
  var motes: Double = 0.0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long = 0
}
