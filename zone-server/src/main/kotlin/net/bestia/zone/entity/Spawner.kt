package net.bestia.zone.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import net.bestia.zone.geometry.Vec3L

/**
 * A spawner is an entity which will control the creation of entities in its vicinity.
 */
// @Entity
// @Table(name = "entity_spawner")
class Spawner {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @OneToMany(mappedBy = "entity", cascade = [CascadeType.ALL], orphanRemoval = true)
  private val components: MutableList<PersistedComponent> = mutableListOf()

  @Embedded
  var position: Vec3L = Vec3L.ZERO

  var width: Int = 100

  var height: Int = 100
}

enum class UsedSpawnStrategy {
  /**
   * Simple timer and most common. Every entity spawn has a min and max delay after a kill
   * which then will control when it will next respawn. If no max_delay is given the respawn
   * will happen exactly at the time of the kill.
   */
  TIMER
}