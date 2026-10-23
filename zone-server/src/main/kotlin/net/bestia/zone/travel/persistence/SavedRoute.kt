package net.bestia.zone.travel.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import org.hibernate.Length

/** A way one master keeps, to send its entities along again by name. */
@Entity
@Table(name = "saved_route", indexes = [Index(name = "idx_saved_route_master", columnList = "master_id")])
class SavedRoute(
  @Column(name = "master_id", nullable = false)
  var masterId: Long = 0,

  @Column(nullable = false, length = 80)
  var name: String = "",

  /** False while [name] is the one made from where the route starts and ends, which follows the world. */
  @Column(name = "custom_name", nullable = false)
  var customName: Boolean = false,

  /** As [net.bestia.zone.travel.WaypointText] writes them. */
  @Lob
  @Column(nullable = false, length = Length.LONG32)
  var waypoints: String = "",
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
