package net.bestia.zone.treasure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * What one treasure map copy shows: the circle somewhere inside which its cache lies. Keyed on the copy, like a
 * chart's `map_chart` row, so it follows the map through trades and the post.
 */
@Entity
@Table(name = "treasure_map", indexes = [Index(name = "idx_treasure_map_cache", columnList = "cache_id")])
class TreasureMapRecord(
  @Id
  @Column(name = "item_instance_id", nullable = false)
  var itemInstanceId: Long = 0,

  @Column(name = "cache_id", nullable = false)
  var cacheId: Long = 0,

  @Column(name = "centre_x", nullable = false)
  var centreX: Double = 0.0,

  @Column(name = "centre_y", nullable = false)
  var centreY: Double = 0.0,

  @Column(nullable = false)
  var radius: Double = 0.0,
)
