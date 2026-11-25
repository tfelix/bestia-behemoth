package net.bestia.zone.worldevent.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** When masters last killed something from a den near a road or a town, so a pack left alone can grow an alpha. */
@Entity
@Table(name = "hunted_den")
class HuntedDen(
  @Id
  @Column(name = "den_feature_id", nullable = false)
  var denFeatureId: Long = 0,

  @Column(name = "last_hunted_second", nullable = false)
  var lastHuntedSecond: Long = 0,
)
