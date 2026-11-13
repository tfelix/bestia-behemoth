package net.bestia.zone.battle.ammo

import net.bestia.zone.battle.RangedWeapon
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Where a shot's ammunition comes from. The item slice answers it from the quiver and the bag. */
interface AmmoSupply {

  /** What [nextFor] and [draw] touch, for the systems that call them to declare. */
  val reads: ComponentClassSet
  val writes: ComponentClassSet

  /** The first quiver entry [weapon] fires that the bag still holds, or null when there is none. */
  fun nextFor(world: World, entityId: EntityId, weapon: RangedWeapon): Ammunition?

  /** Takes one [ammunition] out of the bag. False when none is left, which a stale plan can still ask for. */
  fun draw(world: World, entityId: EntityId, ammunition: Ammunition): Boolean

  /** Whether the bag holds another of the thrown weapon in hand. The one in hand stays to aim with. */
  fun hasThrownSpare(world: World, entityId: EntityId): Boolean

  /** Takes one more of the thrown weapon in hand out of the bag, as a shot; null when none is left. */
  fun drawThrown(world: World, entityId: EntityId): Ammunition?
}
