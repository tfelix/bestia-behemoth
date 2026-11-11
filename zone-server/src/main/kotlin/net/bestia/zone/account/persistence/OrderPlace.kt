package net.bestia.zone.account.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import net.bestia.zone.ai.domain.bestia.NamedPlace

/** A named place of a master's, as stored. See [NamedPlace]. */
@Entity
@Table(name = "order_place", indexes = [Index(columnList = "master_id, name", unique = true)])
class OrderPlace(
  @Column(name = "master_id", nullable = false)
  val masterId: Long,

  @Column(nullable = false, length = NAME_LENGTH)
  var name: String,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  val kind: NamedPlace.Kind,

  @Column(name = "center_x", nullable = false)
  var centerX: Long,

  @Column(name = "center_y", nullable = false)
  var centerY: Long,

  @Column(nullable = false)
  var radius: Long,

  @Column(name = "chest_structure_id")
  val chestStructureId: Long? = null,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  companion object {
    const val NAME_LENGTH = 32

    const val MAX_PER_MASTER = 30
  }
}
