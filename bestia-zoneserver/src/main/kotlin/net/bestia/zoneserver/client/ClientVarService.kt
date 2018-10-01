package net.bestia.zoneserver.client

import mu.KotlinLogging
import net.bestia.model.dao.AccountDAO
import net.bestia.model.dao.ClientVarDAO
import net.bestia.model.dao.findOneOrThrow
import net.bestia.model.domain.ClientVar
import org.springframework.stereotype.Service
import java.util.*

private val LOG = KotlinLogging.logger {  }

/**
 * Service for managing and saving shortcuts coming from the clients to the
 * server.
 *
 * @author Thomas Felix
 */
@Service
class ClientVarService
constructor(
        private val cvarDao: ClientVarDAO,
        private val accDao: AccountDAO
) {

  /**
   * Checks if the given account is the owner of the the variable.
   *
   * @param accId
   * The account ID to check.
   * @param key
   * The key of the var to check.
   * @return TRUE if the account owns this variable. FALSE otherwise.
   */
  fun isOwnerOfVar(accId: Long, key: String): Boolean {
    val cvar = cvarDao.findByKeyAndAccountId(Objects.requireNonNull(key), accId)
    return cvar != null
  }

  /**
   * Delete the cvar for the given account and key.
   *
   * @param accId
   * The account id.
   * @param key
   * The key of the cvar.
   */
  fun delete(accId: Long, key: String) {
    LOG.debug("Deleting cvar with accID: {} and key: {}.", accId, key)
    cvarDao.deleteByKeyAndAccountId(key, accId)
  }

  /**
   * Returns the number of bytes used in total by the given account id.
   *
   * @param accId
   * The account ID to look up.
   * @return The number of bytes used by this account.
   */
  fun getTotalBytesUsedByAccount(accId: Long): Int {
    return cvarDao.findByAccountId(accId)?.sumBy { it.dataLength } ?: 0
  }

  /**
   * Finds the cvar associated with this account id and the key.
   *
   * @param accId
   * The account id.
   * @param key
   * A key.
   * @return The associated cvar variable.
   */
  fun find(accId: Long, key: String): ClientVar {
    LOG.debug("Finding cvar with accID: {} and key: {}.", accId, key)
    return cvarDao.findByKeyAndAccountId(Objects.requireNonNull(key), accId)
  }

  /**
   * Creates or updates client variable.
   *
   * @param accountId
   * The account ID.
   * @param key
   * The key of the variable to set or update.
   * @param data
   * The data payload.
   */
  operator fun set(accountId: Long, key: String, data: String) {
    Objects.requireNonNull(data)

    if (data.length > MAX_DATA_ENTRY_LENGTH_BYTES) {
      val errMsg = String.format("Data can not be longer then %d bytes.", MAX_DATA_ENTRY_LENGTH_BYTES)
      throw IllegalArgumentException(errMsg)
    }

    if (getTotalBytesUsedByAccount(accountId) + data.length >= MAX_DATA_LENGTH_TOTAL_BYTES) {
      val errMsg = String.format("Max data stored can not be longer then %d bytes.",
              MAX_DATA_LENGTH_TOTAL_BYTES)
      throw IllegalArgumentException(errMsg)
    }

    var cvar: ClientVar? = find(accountId, key)

    if (cvar != null) {
      cvar.data = data
    } else {
      // Cvar is not yet set. Just create one.
      val acc = accDao.findOneOrThrow(accountId)
      cvar = ClientVar(acc, key, data)
    }

    cvarDao.save(cvar)
  }

  companion object {
    private const val MAX_DATA_ENTRY_LENGTH_BYTES = 1000
    private const val MAX_DATA_LENGTH_TOTAL_BYTES = 100 * 1024
  }
}