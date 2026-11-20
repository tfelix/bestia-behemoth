package net.bestia.zone.stronghold.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * A master walked onto an awake Empyrite Seed. From then on Official Guild Approval names it for them.
 *
 * A seed's rank only names the same seed under the same world, so the row carries both versions, as
 * [EmpyriteSeedState] does.
 */
@Entity
@Table(name = "empyrite_seed_discovery")
class EmpyriteSeedDiscovery(
  @EmbeddedId
  var id: Key = Key(),

  @Column(name = "discovered_at", nullable = false)
  var discoveredAt: Instant = Instant.EPOCH,

  @Column(name = "world_shape_version", nullable = false)
  var worldShapeVersion: Long = 0,

  @Column(name = "pipeline_version", nullable = false)
  var pipelineVersion: Long = 0,
) {

  @Embeddable
  data class Key(
    @Column(name = "master_id", nullable = false)
    var masterId: Long = 0,

    @Column(name = "seed_rank", nullable = false)
    var rank: Int = 0,
  ) : Serializable
}
