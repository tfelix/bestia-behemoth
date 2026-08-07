package net.bestia.zone.ecs.prop

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/**
 * Intent to take the static prop [propEntityId] into this entity's inventory.
 *
 * Attached by `CollectPropHandler` and resolved by `CollectPropIntentSystem`, which is where every check
 * lives - kind, range, and whether the prop has already been claimed.
 *
 * ### Why the handler cannot just do the work
 *
 * Claiming a prop means writing [net.bestia.zone.world.prop.WorldObjectDivergenceRegistry], whose KDoc states
 * it is a plain `HashMap` touched exclusively from the tick thread and that this is what makes it correct
 * rather than merely convenient. Message handlers run on Netty threads, and the world lock does not cover
 * that map - it is off the ECS entirely. So the write has to happen inside a `System.update`, and an intent
 * component is how a handler asks for that.
 *
 * ### Why this is not an [net.bestia.zone.ecs.item.ObtainItemIntent]
 *
 * That sealed class is "add an item to an inventory". This is "consume a world object, which happens to yield
 * one" - it needs the prop registry, the divergence registry and the residency service, none of which belong
 * in an item system whose own capacity check is deliberately weight-only. The prop side calls the item side
 * instead, which is the arrangement `PropDeathDivergenceSystem` already uses.
 *
 * Only one can be attached at a time, since components are stored one-per-class per entity: a player who
 * clicks two props inside the same 50 ms tick collects the second. Matches `LootItemIntent`'s existing
 * behaviour and is not worth a queue.
 */
data class CollectPropIntent(val propEntityId: EntityId) : Component
