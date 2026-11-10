package net.bestia.zone.treasure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Which artifact of the world's chronicle a relic copy is. Its name is read off the chronicle, not stored. */
@Entity
@Table(name = "treasure_relic")
class RelicRecord(
  @Id
  @Column(name = "item_instance_id", nullable = false)
  var itemInstanceId: Long = 0,

  @Column(nullable = false)
  var artifact: Int = 0,
)
