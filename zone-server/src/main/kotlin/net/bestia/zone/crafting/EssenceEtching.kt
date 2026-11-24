package net.bestia.zone.crafting

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.essence.Essence
import net.bestia.zone.util.EntityId

/**
 * The Bestia side of Runic Etching. Crafting runs the craft; what a Bestia may be etched and how it leaves the game
 * belong to the Bestias, a slice above this one.
 */
interface EssenceEtching {

  /** Why [masterId] cannot etch [bestiaId] into [gemItemId], or null when the etching may go ahead. */
  fun refusal(world: World, masterId: Long, bestiaId: EntityId, gemItemId: Long): OpError?

  /** Takes [bestiaId] out of the game and answers the essence it leaves in [gemItemId], or null if it is gone. */
  fun bind(world: World, bestiaId: EntityId, gemItemId: Long): Essence?

  /** The master entity the Essence Gem of [bestiaId] goes to: its owner, who need not be the etcher. */
  fun ownerOf(world: World, bestiaId: EntityId): EntityId?

  /** Other masters' Bestias offered for etching at the Carving Table at [tablePosition]. */
  fun offersAt(world: World, tablePosition: Vec3L, etcherMasterId: Long): List<EtchingOffer>
}
