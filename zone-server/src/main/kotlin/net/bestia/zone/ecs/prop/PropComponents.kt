package net.bestia.zone.ecs.prop

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.prop.StaticEntityKind

/**
 * Where a static entity stands, and which way it faces.
 *
 * ### Why this is not [net.bestia.zone.ecs.movement.Position]
 *
 * `Position` is `Dirtyable`, and `ZoneEngine.syncDirtyComponents` walks every `Dirtyable` store on every one
 * of the twenty ticks a second - so a resident population of tens of thousands of things that never move would
 * be scanned two hundred thousand times a second to discover that none of them changed. There is no mechanism
 * to opt out: a `Dirtyable` sets its own flag from inside its own setters with no reference to the world or its
 * own id, so it cannot enqueue itself onto a dirty list, and the only way out of the scan is not to be in the
 * store.
 *
 * Being out of the `Position` store buys three more things, each of which would otherwise be a per-tick cost
 * for no benefit: `ChunkStreamSystem.groundNewcomers` scans the whole `Position` store every tick looking for
 * ungrounded entities, `MoveSystem` queries it, and the area-of-interest index is fed from its dirty flag.
 *
 * A static entity **is** in the interest octree - an area-of-effect spell has to find a tree - but it is put
 * there directly by the residency service rather than through the dirty-position path. Those two things looked
 * coupled and are not.
 *
 * Promoted on interaction: the moment something targets or damages one, `Position` and `Health` are added and
 * it becomes an ordinary entity for as long as that lasts.
 */
data class PropPose(
  val position: Vec3L,

  /** Radians, so a client can turn a tree rather than planting a forest of clones all facing the same way. */
  val yaw: Float
) : Component

/**
 * What a static entity looks like.
 *
 * Not `Dirtyable`: the initial state travels in the per-chunk batch that carries the ground it stands on, and
 * after that it does not change until something interacts with it. Contrast `EntityVisual`, which is
 * `Dirtyable` because a mob arrives one entity at a time.
 *
 * Carries only what the client cannot derive - the kind, a variant roll, the height the generator drew, and a
 * footprint for the one kind whose size is not a property of its kind - on the same argument `EntityVisual`
 * makes by sending a catalogue id and nothing else.
 */
data class StaticVisual(
  val kind: StaticEntityKind,

  /** A stable roll for picking between interchangeable meshes of one kind, so a wood is not one tree repeated. */
  val variant: Int,

  /** Decimetres. A tree is 4.5 to 12 m, so a byte of decimetres is finer than anyone can see and a third of an int. */
  val heightDm: Int,

  /**
   * Footprint half-extents in decimetres, along and across [PropPose.yaw], or **0 for a kind that has none**.
   *
   * Zero everywhere but a building, and that asymmetry is the point rather than a gap. Every other kind is
   * radially symmetric and takes its size from its `prop-kinds.yml` row, which is where a size that is a
   * property of the *kind* belongs; a building's is a property of the **lot**, decided by `TownStage` when it
   * cut the plot, and no per-kind number can stand in for it because a temple and a barn are both buildings.
   */
  val halfLengthDm: Int = 0,
  val halfWidthDm: Int = 0
) : Component

/**
 * How much punishment a static entity can take, before it has taken any.
 *
 * Not [net.bestia.zone.ecs.battle.status.Health], for the reason [PropPose] is not `Position`, and with a
 * second one on top: being in the `Health` store puts an entity in front of `HpRegenSystem`, `DeathSystem` and
 * `ReceivedDamageSystem`, all of which query it directly. A pristine tree has nothing for any of them to do.
 *
 * `Health` is added by the promotion path on the first point of damage, seeded from this.
 */
data class PropVitality(val maxHp: Int) : Component

/**
 * The durable name of a generated static entity: its kind and its lattice cell, from `worldgen`'s `PropId`.
 *
 * The **entity id is not** that name. A prop's entity id is a fresh snowflake every time its chunk is
 * re-materialised, because residency destroys and recreates rather than caching - so anything that has to
 * survive a chunk leaving the view is keyed on this instead. `world_object_delta` is keyed on it.
 *
 * [latticeVersion] is what makes a stored key falsifiable. A cell index is a position quantisation, so
 * changing `VegetationParams.cellSize` renames every prop in the world; a row carrying the version it was
 * written under can be recognised as orphaned rather than silently applied to a different tree.
 */
data class WorldObjectIdentity(
  val propId: Long,
  val latticeVersion: Long
) : Component

/**
 * Marker: this entity reaches clients through the per-chunk static batch, not through per-component messages.
 *
 * The discriminator for the third of three independent questions - who exists, what survives a restart, and
 * how it reaches the client - and it is deliberately *only* the third. A generated tree and a player-built wall
 * answer the first two completely differently and this one identically, which is the whole reason it is a
 * marker of its own rather than a property of how the entity was made or how it is stored.
 */
object StaticSync : Component
