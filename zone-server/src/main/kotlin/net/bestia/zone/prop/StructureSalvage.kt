package net.bestia.zone.prop

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/** What a structure knocked down by another master leaves them. The slice that knows the kits implements it. */
interface StructureSalvage {

  /** What [salvage] touches, for the death system to declare. */
  val reads: ComponentClassSet
  val writes: ComponentClassSet

  fun salvage(world: World, structure: StructureEntry, at: Vec3L, salvagerId: EntityId, salvagerAccountId: AccountId)
}
