package net.bestia.zone.worldboss.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A lair whose world boss players killed. It stays dead for the rest of this world.
 *
 * Keyed by the lair marker's feature id, which only names the same lair under the same pipeline version, so the
 * row carries that version and the world id.
 */
@Entity
@Table(name = "world_boss_slain")
class SlainWorldBoss(
  @Id
  @Column(name = "lair_feature_id", nullable = false)
  var lairFeatureId: Long = 0,

  @Column(name = "world_id", nullable = false)
  var worldId: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,

  @Column(name = "bestia_id", nullable = false)
  var bestiaId: Long = 0,

  /** The account that dealt the most damage. */
  @Column(name = "slain_by", nullable = false)
  var slainBy: Long = 0,

  @Column(name = "slain_at", nullable = false)
  var slainAt: Instant = Instant.EPOCH,
)
