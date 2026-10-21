package net.bestia.zone.session

import net.bestia.zone.BestiaEvent
import net.bestia.zone.util.AccountId

/**
 * A master started or stopped being played: selected, re-attached, logged out, or its connection went away.
 *
 * Published by [ConnectionInfoService] right after the session changed, from whichever thread changed it - the
 * IO lane, the tick, or inside a world lease. A listener may therefore only hand work off, never block, read
 * the database or take the world.
 */
class MasterPresenceChangedEvent(
  source: Any,
  val accountId: AccountId,
  val masterId: Long,
  val online: Boolean,
) : BestiaEvent(source)
