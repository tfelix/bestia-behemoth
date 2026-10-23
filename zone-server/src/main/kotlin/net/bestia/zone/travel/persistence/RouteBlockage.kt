package net.bestia.zone.travel.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * A macro edge one master believes is blocked, until when.
 *
 * Edge indices are only meaningful in the world that generated them, hence the version stamps, as
 * `GroundLayerMark` keeps them.
 */
@Entity
@Table(name = "route_blockage")
class RouteBlockage(
  @EmbeddedId
  var id: Key = Key(),

  @Column(name = "lapses_at", nullable = false)
  var lapsesAt: Instant = Instant.EPOCH,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {

  @Embeddable
  data class Key(
    @Column(name = "master_id", nullable = false)
    var masterId: Long = 0,

    @Column(name = "edge_index", nullable = false)
    var edgeIndex: Int = 0,
  ) : Serializable
}
