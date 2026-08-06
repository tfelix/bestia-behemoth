package net.bestia.zone.world

import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bestia.zone.geometry.Vec3L

/**
 * A candidate "home settlement" a new master can choose to spawn near.
 *
 * No world-id column: the codebase assumes a single world row throughout
 * ([WorldRepository.findFirstByOrderByIdAsc]), and this table is fully cleared whenever the world
 * row is replaced (see [WorldProvisioning.recreate]), so "any row present" already means "belongs to
 * the current world" - the same assumption [net.bestia.zone.entity.PersistedEntity] makes.
 */
@Entity
@Table(name = "master_spawn_point")
class MasterSpawnPoint(
  @Column(nullable = false)
  val settlementIndex: Int,

  @Column(nullable = false, length = 64)
  val settlementName: String,

  @Column(nullable = false, length = 32)
  val tier: String,

  @Column(nullable = false)
  val population: Int,

  @Embedded
  @AttributeOverrides(
    AttributeOverride(name = "x", column = Column(name = "position_x")),
    AttributeOverride(name = "y", column = Column(name = "position_y")),
    AttributeOverride(name = "z", column = Column(name = "position_z"))
  )
  val position: Vec3L
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
