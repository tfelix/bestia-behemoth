package net.bestia.zone.world.prop

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * One thing a player put up: a workbench, a furnace, a forge.
 *
 * A row rather than in-memory config, which is the opposite of everything else in this package -
 * `prop-kinds.yml` is configuration and `WorldObjectDivergence` records a *deviation* from what the
 * generator would produce. This is neither: nothing generated it, so there is nothing for it to deviate
 * from, and it has to survive a restart because a player spent materials on it.
 *
 * Indexed on the chunk column because that is the only way it is ever read - [PlayerStructureRegistry]
 * loads the table once at boot and answers per column from memory, so the index earns its keep at boot
 * and after a placement rather than on every query.
 */
@Entity
@Table(
  name = "player_structure",
  indexes = [Index(columnList = "chunk_x,chunk_y")]
)
class PlayerStructure(
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  val kind: StaticEntityKind,

  /** Who built it. Unowned structures do not exist, and taking one down is their owner's right. */
  @Column(name = "owner_master_id", nullable = false)
  val ownerMasterId: Long,

  @Column(name = "pos_x", nullable = false)
  val x: Long,

  @Column(name = "pos_y", nullable = false)
  val y: Long,

  @Column(name = "pos_z", nullable = false)
  val z: Long,

  /** Radians, so a forge can face the way its builder was facing rather than always north. */
  @Column(nullable = false)
  val yaw: Float,

  /**
   * Denormalised chunk column, written once at placement.
   *
   * Derived from [x]/[y] and the world's chunk size, which is exactly why it is stored: the chunk size is a
   * property of the *world*, and computing the column at query time would make every read of this table
   * depend on `WorldService` being loaded. It also lets the index above exist at all.
   */
  @Column(name = "chunk_x", nullable = false)
  val chunkX: Int,

  @Column(name = "chunk_y", nullable = false)
  val chunkY: Int
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
