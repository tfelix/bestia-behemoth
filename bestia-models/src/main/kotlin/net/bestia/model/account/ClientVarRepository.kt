package net.bestia.model.account

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import net.bestia.model.account.ClientVar

@Repository
interface ClientVarRepository : CrudRepository<ClientVar, Long> {

  fun findByKey(key: String): ClientVar?

  /**
   * Searches for a client variable.
   *
   * @param key
   * The key of the client var.
   * @param accountId
   * The owning account id.
   * @return Returns the found client variable or null if no was found.
   */
  fun findByKeyAndAccountId(key: String, accountId: Long): ClientVar?

  /**
   * Deletes a client variable identified by its key and the owning account
   * id.
   *
   * @param key
   * The key of the client var.
   * @param accountId
   * The owning account id.
   */
  fun deleteByKeyAndAccountId(key: String, accountId: Long)

  /**
   * Counts the number of bytes used by a single account.
   *
   * @param accountId
   * The account to find.
   * @return
   */
  fun findByAccountId(accountId: Long): List<ClientVar>
}
