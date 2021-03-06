package net.bestia.zoneserver.account

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.account.ClientVarRepository
import net.bestia.model.findOneOrThrow
import net.bestia.model.account.ClientVar
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * Service for managing and saving shortcuts coming from the clients to the
 * server.
 *
 * @author Thomas Felix
 */
@Service
class ClientVarService
constructor(
    private val cvarDao: ClientVarRepository,
    private val accDao: AccountRepository
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
    val data = cvarDao.findByKey(key)
        ?: return true
    return data.account.id == accId
  }

  /**
   * Delete the cvar for the given account and key.
   *
   * @param accId
   * The account id.
   * @param key
   * The key of the cvar.
   */
  fun deleteCvar(accId: Long, key: String) {
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
    return cvarDao.findByAccountId(accId).sumBy { it.dataLength }
  }

  /**
   * Finds the cvar associated with this account id and the key.
   *
   * @param accId
   * The account id.
   * @param key A key.
   * @return The associated cvar variable.
   */
  fun findCvar(accId: Long, key: String): ClientVar {
    return tryFind(accId, key)
        ?: throw IllegalArgumentException("No cvar for account $accId with key: $key")
  }

  fun tryFind(accId: Long, key: String): ClientVar? {
    LOG.debug { "Find cvar with accID: $accId and key: $key." }

    return cvarDao.findByKeyAndAccountId(key, accId)
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
  fun setCvar(accountId: Long, key: String, data: String) {
    LOG.debug { "Set for accId: $accountId, key: $key -> $data" }

    require(data.isNotEmpty()) {
      "Data can not be empty. Use delete() to delete cvars."
    }
    require(data.length < MAX_DATA_ENTRY_LENGTH_BYTES) {
      "Data can not be longer then $MAX_DATA_ENTRY_LENGTH_BYTES bytes, was: ${data.length}."
    }
    require(getTotalBytesUsedByAccount(accountId) + data.length <= MAX_DATA_LENGTH_TOTAL_BYTES) {
      "Max data stored can not be longer then $MAX_DATA_LENGTH_TOTAL_BYTES bytes."
    }

    var cvar: ClientVar? = tryFind(accountId, key)

    if (cvar != null) {
      cvar.setData(data)
    } else {
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