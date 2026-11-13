package net.bestia.zone.world.stream

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Service

/**
 * A second place an account looks at, away from its body, like a Sage's Scry. [ChunkStreamSystem] offers the terrain
 * around it on top of the body's view, and entities follow the terrain. The body's view stays held, so closing a
 * viewpoint only withdraws the far chunks. Tick thread only, like the rest of the streaming layer.
 */
@Service
class RemoteViewpoints {

  /** [radiusChunks] is horizontal, like [ChunkStreamConfig.viewRadiusChunks]. */
  data class Viewpoint(val centre: Vec3L, val radiusChunks: Int)

  private val byAccount = HashMap<AccountId, Viewpoint>()

  /** Replaces the account's viewpoint, if it had one. */
  fun open(accountId: AccountId, centre: Vec3L, radiusChunks: Int) {
    require(radiusChunks >= 0) { "A viewpoint radius cannot be negative" }
    byAccount[accountId] = Viewpoint(centre, radiusChunks)
  }

  fun close(accountId: AccountId) {
    byAccount.remove(accountId)
  }

  fun of(accountId: AccountId): Viewpoint? {
    return byAccount[accountId]
  }
}
