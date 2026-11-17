package net.bestia.zone.poi.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * A POI players destroyed. It stays gone for the rest of this world.
 *
 * (kind, instanceId) only names the same POI under the same pipeline version, so the row carries that version
 * and the world id.
 */
@Entity
@Table(name = "poi_destroyed")
class DestroyedPoi(
  @EmbeddedId
  var id: Key = Key(),

  @Column(name = "world_id", nullable = false)
  var worldId: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,

  @Column(name = "destroyed_at", nullable = false)
  var destroyedAt: Instant = Instant.EPOCH,
) {

  @Embeddable
  data class Key(
    /** `PoiKind` name, so a reordered catalogue cannot point a row at another POI. */
    @Column(name = "kind", nullable = false, length = 64)
    var kind: String = "",

    @Column(name = "instance_id", nullable = false)
    var instanceId: Int = 0,
  ) : Serializable
}
