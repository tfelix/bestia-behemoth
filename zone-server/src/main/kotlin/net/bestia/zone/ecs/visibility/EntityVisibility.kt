package net.bestia.zone.ecs.visibility

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * Told where the dynamic entities are, so whatever decides who can see them can keep up.
 *
 * A seam, the same shape as [net.bestia.zone.ecs.movement.GroundHeight] beside it: the answer is the chunk
 * subscription a client holds for its terrain, which lives in `world.stream`, and `ZoneEngine` is the only
 * place that knows when an entity moved. This lets it say so without pointing `ecs/` at `world/stream/`.
 *
 * **Only safe to call from the tick thread** - the implementation reads the streaming layer. [reannounce] is
 * the one exception, and says so.
 */
interface EntityVisibility {

  /**
   * Records that [entityId] now stands at [position].
   *
   * Dynamic entities only. A static one - a tree, a wall, a promoted prop - reaches clients on the map channel
   * with its chunk's batch, so announcing it here would deliver it twice.
   */
  fun moved(entityId: EntityId, position: Vec3L)

  /**
   * Records that [entityId] has left the world. Emits no departure - a destroyed entity already gets a
   * [net.bestia.zone.entity.VanishEntitySMSG] saying whether it died, which is more than "out of sight".
   */
  fun forgot(entityId: EntityId)

  /**
   * The accounts told [entityId] exists and not yet told otherwise, and so the audience for anything that
   * changes about it.
   *
   * Not the same set as a range query: the view volume lets an account hold a chunk from a good deal further
   * away than an interest radius reaches, so a radius audience leaves the outer holders drawing stale state.
   */
  fun observersOf(entityId: EntityId): Set<AccountId>

  /**
   * Asks for [accountId] to be told again about every entity it can already see, as if each had just come
   * into view.
   *
   * A client only becomes able to receive entity messages once its game scene is built, which is seconds
   * after `SelectMasterCMSG` starts the world streaming to it - and the arrivals in between are announced
   * exactly once and never repeated. That includes the player's own master, so without this the body the
   * camera follows never gets a position or a visual and the world looks empty. `GetSelfHandler` is where
   * the client says it is listening, and this is how that becomes an answer.
   *
   * **Safe to call from any thread**, unlike the rest of this interface: it only records the request, which
   * is served by the next [drain] on the tick thread.
   */
  fun reannounce(accountId: AccountId)

  /**
   * Takes the visibility changes accumulated since the last call, and clears them.
   *
   * Drained by `ZoneEngine` after the tick: the only place on the tick thread with no system running and
   * outside the world lock, and so the only place a snapshot may safely be built.
   */
  fun drain(): List<Delivery>

  /**
   * What one account has to be told this tick. [appeared] and [vanished] are disjoint: an entity that both
   * arrived and left within one tick has already been resolved to whichever happened last.
   */
  data class Delivery(
    val accountId: AccountId,
    val appeared: List<EntityId>,
    val vanished: List<EntityId>
  )
}
