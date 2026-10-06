package net.bestia.zone.ecs.prop

import net.bestia.zone.ecs.core.Component

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
