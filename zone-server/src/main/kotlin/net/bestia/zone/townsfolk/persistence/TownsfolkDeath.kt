package net.bestia.zone.townsfolk.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * A townsperson who was killed, and the Bestia day somebody takes their place. Deleted once they have, so the
 * table holds only recent deaths. Stamped with the world versions for `Rumour`'s reason.
 */
@Entity
@Table(name = "townsfolk_death")
class TownsfolkDeath(
  /** A `TownsfolkIdentity`. */
  @Id
  @Column(nullable = false)
  var identity: Long = 0,

  @Column(name = "replaced_on_day", nullable = false)
  var replacedOnDay: Double = 0.0,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
)
