package net.bestia.zone.crafting

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.essence.Essence
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId

/**
 * Runic Etching on one of the etcher's own Bestias in storage. Storage lives in the database and the craft on the
 * tick, so the tick answers from what [load] last read, and the database is asked again before anything is spent.
 * Implemented by the Bestia storage, a slice above this one.
 */
interface StoredEtching {

  /** Why [masterEntityId] cannot reach its storage now, or null. On the tick. */
  fun accessRefusal(world: World, masterEntityId: EntityId): OpError?

  /** The stored Bestias of [masterId], read from the database and kept for the tick. Off the tick. */
  fun load(masterId: Long): List<StoredEtchable>

  /** Why the stored Bestia cannot fill [gemItemId], from the last [load]. On the tick. */
  fun refusal(masterId: Long, playerBestiaId: PlayerBestiaId, gemItemId: Long): OpError?

  /** Its level, from the last [load]; 0 if it was not there. */
  fun levelOf(masterId: Long, playerBestiaId: PlayerBestiaId): Int

  /** Whether it is still in storage, as the database has it now. Off the tick. */
  fun isStillStored(masterId: Long, playerBestiaId: PlayerBestiaId): Boolean

  /** Takes the stored Bestia out of the game and answers its essence in [gemItemId]. Off the tick. */
  fun bind(masterId: Long, playerBestiaId: PlayerBestiaId, gemItemId: Long): Essence?
}

/** One of the etcher's own Bestias in storage, as the etching picker lists it. */
data class StoredEtchable(val playerBestiaId: PlayerBestiaId, val speciesId: Long, val level: Int, val name: String?)
