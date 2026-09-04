package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ecs.visibility.EntityVisibility
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Which dynamic entities stand in which chunk, so "who can see this entity" is answered from the subscription
 * a client already holds for that chunk's terrain rather than from a second range query.
 *
 * Thin for the reason [ChunkGroundHeight] is thin: the question belongs to `ecs/`, the chunk arithmetic here.
 * Chunks are stored **normalised**, because that is how [ChunkSubscriptionService] keys the subscription - on
 * a wrapping world an un-normalised key matches no holder.
 *
 * Tick thread only, like the rest of the streaming layer.
 */
@Component
class ChunkEntityVisibility(
  private val chunkService: ChunkService,
  private val subscriptions: ChunkSubscriptionService
) : EntityVisibility {

  init {
    // The entities in a chunk become visible with the ground under them and stop being visible with it.
    // Enqueue and return: these fire from inside ChunkStreamSystem's own update.
    subscriptions.onChunkSent { accountId, chunk -> residentsOf(chunk).forEach { appear(accountId, it) } }
    subscriptions.onChunkUnsent { accountId, chunk -> residentsOf(chunk).forEach { vanish(accountId, it) } }
  }

  /**
   * Two halves of one index, in one class because they have to agree: [residents] answers "who is standing
   * here" for an arriving subscriber, [chunkOfEntity] "where was it" for a moving entity.
   */
  private val residents = HashMap<ChunkPos, MutableSet<EntityId>>()
  private val chunkOfEntity = HashMap<EntityId, ChunkPos>()

  /**
   * What each account is owed until `ZoneEngine` drains it. Two sets per account rather than a list of events,
   * so an entity that arrives and leaves inside one tick resolves to one answer - see [appear] and [vanish].
   */
  private val pendingAppear = HashMap<AccountId, MutableSet<EntityId>>()
  private val pendingVanish = HashMap<AccountId, MutableSet<EntityId>>()

  override fun moved(entityId: EntityId, position: Vec3L) {
    val move = reindex(entityId, position) ?: return

    // Only the accounts whose answer changed. Crossing between two chunks the same account holds - most
    // crossings, the view being eleven chunks across - is nobody's news.
    val before = move.from?.let { subscriptions.subscribersOf(it) } ?: emptySet()
    val after = subscriptions.subscribersOf(move.to)

    after.filterNot { it in before }.forEach { appear(it, entityId) }
    before.filterNot { it in after }.forEach { vanish(it, entityId) }
  }

  override fun forgot(entityId: EntityId) {
    val from = chunkOfEntity.remove(entityId) ?: return
    detach(entityId, from)

    // A destroy broadcasts its own vanish, so nothing is queued here - but anything already queued about
    // this entity has to go, or a snapshot gets built for an entity that no longer exists.
    pendingAppear.forget(entityId)
    pendingVanish.forget(entityId)
  }

  override fun observersOf(entityId: EntityId): Set<AccountId> {
    val chunk = chunkOfEntity[entityId] ?: return emptySet()

    return subscriptions.subscribersOf(chunk)
  }

  override fun drain(): List<EntityVisibility.Delivery> {
    if (pendingAppear.isEmpty() && pendingVanish.isEmpty()) return emptyList()

    val deliveries = (pendingAppear.keys + pendingVanish.keys).mapNotNull { accountId ->
      val appeared = pendingAppear[accountId]?.toList() ?: emptyList()
      val vanished = pendingVanish[accountId]?.toList() ?: emptyList()

      if (appeared.isEmpty() && vanished.isEmpty()) null
      else EntityVisibility.Delivery(accountId, appeared, vanished)
    }

    pendingAppear.clear()
    pendingVanish.clear()

    return deliveries
  }

  private fun appear(accountId: AccountId, entityId: EntityId) {
    pendingVanish[accountId]?.remove(entityId)
    pendingAppear.getOrPut(accountId) { LinkedHashSet() }.add(entityId)
  }

  private fun vanish(accountId: AccountId, entityId: EntityId) {
    pendingAppear[accountId]?.remove(entityId)
    pendingVanish.getOrPut(accountId) { LinkedHashSet() }.add(entityId)
  }

  /** The chunk [position] falls in, normalised. */
  fun chunkAt(position: Vec3L): ChunkPos =
    chunkService.normalise(ChunkCoords.chunkOf(chunkService.config, position))

  /** The dynamic entities standing in [chunk]; a copy, so a caller may hand it off the tick thread. */
  fun residentsOf(chunk: ChunkPos): List<EntityId> = residents[chunk]?.toList() ?: emptyList()

  /** Where the index believes [entityId] stands, or null if it holds no entry for it. */
  fun chunkHolding(entityId: EntityId): ChunkPos? = chunkOfEntity[entityId]

  /**
   * @return the move it just recorded, or null when the entity stayed in the chunk it was already in - which
   *   is the common case, since crossing a chunk edge takes `chunkSize` tiles of walking.
   */
  fun reindex(entityId: EntityId, position: Vec3L): Transition? {
    val to = chunkAt(position)
    val from = chunkOfEntity.put(entityId, to)

    if (from == to) return null

    from?.let { detach(entityId, it) }
    residents.getOrPut(to) { HashSet() }.add(entityId)

    return Transition(from, to)
  }

  /** Drops [entityId] from every account's queue, and any account left with nothing queued. */
  private fun HashMap<AccountId, MutableSet<EntityId>>.forget(entityId: EntityId) {
    values.forEach { it.remove(entityId) }
    values.removeIf { it.isEmpty() }
  }

  private fun detach(entityId: EntityId, chunk: ChunkPos) {
    val remaining = residents[chunk] ?: return
    remaining.remove(entityId)
    if (remaining.isEmpty()) residents.remove(chunk)
  }

  /** [from] is null when the index had no entry yet, which is how a spawn reads. */
  data class Transition(val from: ChunkPos?, val to: ChunkPos)
}
