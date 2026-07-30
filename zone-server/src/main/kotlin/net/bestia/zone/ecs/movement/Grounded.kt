package net.bestia.zone.ecs.movement

import net.bestia.zone.ecs.core.Component

/**
 * Marks an entity whose vertical position has been reconciled with the terrain at least once.
 *
 * ### What its absence means
 *
 * That the entity's `z` is a *guess*. The authoritative ground elevation belongs to `ChunkService`, which only
 * the tick thread may ask, so anything producing a position on a request thread - master creation, a script
 * placing an entity - has to invent one. Get it wrong downwards and the entity is inside solid rock: every
 * chunk around it is uniform stone, which encodes to twelve bytes, meshes to no surface at all, and renders as
 * a black screen that looks exactly like the terrain failing to load. The same applies to any position loaded
 * from the database, which was written by whatever convention was current when it was saved.
 *
 * Master spawn points do better than a guess - `MasterSpawnPointService` samples the generator's own height
 * field when it computes them - but "sampled from the height field" and "reconciled with the chunk the player
 * is standing in" are not the same claim, and this marker only makes the second one.
 *
 * So rather than have every producer of a position solve a problem it cannot reach the data for, an ungrounded
 * entity is snapped once on the first tick it is seen - see `ChunkStreamSystem.groundNewcomers`.
 *
 * ### Why a marker rather than a heuristic
 *
 * The tempting alternative is to snap any entity found below the terrain. That is self-correcting and needs no
 * state, and it also silently teleports anything legitimately underground to the surface - which is fine only
 * for exactly as long as there are no caves, and the architecture document already has them planned. A marker
 * says "this has been checked" and stays true when the world grows a below-ground somewhere worth being.
 */
data object Grounded : Component
