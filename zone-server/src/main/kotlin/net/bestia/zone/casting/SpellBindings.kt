package net.bestia.zone.casting

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * Spell Binding's choices, read from the cast's args; the spellbinding slice implements it. The args stay raw here
 * because their shape belongs to that slice.
 */
interface SpellBindings {

  /** Why the caster cannot make the binding [args] ask for, or null when it can. */
  fun bindRefusal(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError?

  /** Takes the scroll and binds it at [at], or to the item [args] name. The refusal, or null once bound. */
  fun bind(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>, at: Vec3L, potency: Int): OpError?

  /** Releases one of the caster's own bindings when [args] ask for that; false for any other choice. */
  fun release(world: World, casterId: EntityId, args: List<Long>): Boolean
}
