package net.bestia.zone.world.stream

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** What a carve gives the entity that made it. The item slice knows which voxels are worth something. */
interface CarveYield {

  /** The component types [bank] reads. */
  val reads: ComponentClassSet

  /** The component types [handOut] writes. */
  val writes: ComponentClassSet

  /** Puts aside for [entityId] what [result] broke, or for the ground when it [scatters]. Tick thread. */
  fun bank(world: World, result: ChunkService.CarveResult, entityId: EntityId, scatters: Boolean = false)

  /** Hands out what is put aside. Tick thread. */
  fun handOut(world: World)
}
