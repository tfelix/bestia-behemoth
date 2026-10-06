package net.bestia.zone.item.container

import net.bestia.zone.util.PlayerBestiaId

/**
 * Loads and saves the rows that hold item containers. The account slice owns those rows, so it implements this.
 *
 * Every container write loads the container, changes it and saves it, from several kinds of threads at once. So
 * each load locks its row until the transaction ends; without the lock two writers read the same state and one
 * change is lost.
 */
interface ContainerOwners {

  /** Locks the master's row. Throws when there is no such master. */
  fun lockedMaster(masterId: Long): ContainerOwner

  /** Locks the player bestia's row. Throws when there is no such player bestia. */
  fun lockedPlayerBestia(playerBestiaId: PlayerBestiaId): ContainerOwner

  fun save(owner: ContainerOwner)

  /** Saves and flushes, and answers the saved row, whose new container slots now have ids. */
  fun saveAndFlush(owner: ContainerOwner): ContainerOwner
}
