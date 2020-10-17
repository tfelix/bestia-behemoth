package net.bestia.model.bestia

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

/**
 * DAO for accessing and manipulating the [PlayerBestia]s.
 *
 * @author Thomas Felix
 */
@Repository
interface PlayerBestiaRepository : CrudRepository<PlayerBestia, Long> {

  /**
   * Finds all [PlayerBestia]s for a given account ID.
   *
   * @param accId Account ID to get all bestias.
   * @return A set of all found [PlayerBestia]s for this account.
   */
  fun findAllByOwnerId(accId: Long): Set<PlayerBestia>

  fun findByOwnerIdAndId(accountId: Long, id: Long): PlayerBestia?
}
