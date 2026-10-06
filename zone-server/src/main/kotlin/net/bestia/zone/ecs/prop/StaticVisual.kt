package net.bestia.zone.ecs.prop

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.world.prop.StaticEntityKind

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
