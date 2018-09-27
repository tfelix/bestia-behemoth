package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.model.dao.BestiaAttackDAO
import net.bestia.model.dao.PlayerBestiaDAO
import net.bestia.model.dao.findOneOrThrow
import net.bestia.model.domain.BestiaAttack
import net.bestia.model.domain.PlayerBestia
import net.bestia.model.domain.PlayerItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * The service for managing and editing of the player bestias.
 *
 * @author Thomas Felix
 */
@Service
@Transactional
class PlayerBestiaService(
        private val playerBestiaDao: PlayerBestiaDAO,
        private val attackDao: BestiaAttackDAO
) {
  /**
   * Returns all attacks for a certain player bestia with the given player
   * bestia id.
   *
   * @return A list of attacks usable by the given player bestia id.
   */
  fun getAllAttacksForPlayerBestia(playerBestiaId: Long): List<BestiaAttack> {
    LOG.trace("Retrieving all attacks for player bestia {}", playerBestiaId)
    val pb = playerBestiaDao.findOneOrThrow(playerBestiaId)
    return attackDao.getAllAttacksForBestia(pb.origin.getId())
  }

  /**
   * The item ids are looked up and a reference is saved for the bestia. The
   * player must have this item in his inventory.
   *
   * @return Returns the checked item shortcut slot array. If the bestia does
   * not own this item the slot will be null otherwise it will contain
   * the item.
   */
  fun saveItemShortcuts(playerBestiaId: Long, itemIds: List<Int>): Array<PlayerItem?> {
    LOG.trace("Saving item shortcuts {} for player bestia {}", itemIds, playerBestiaId)

    if (itemIds.size != 5) {
      throw IllegalArgumentException(
              "The size of the item slot array must be 5. Fill empty slots with null.")
    }

    val bestia = playerBestiaDao.findOneOrThrow(playerBestiaId)
    val checkedItems = arrayOfNulls<PlayerItem>(NUM_ITEM_SLOTS)
    playerBestiaDao.save(bestia)

    return checkedItems
  }

  /**
   * Returns all the bestias under a given account id. This includes the
   * bestia master as well as "normal" bestias.
   *
   * @param accId
   * @return Returns the set of player bestia for a given account id or an
   * empty set if this account does not exist.
   */
  fun getAllBestias(accId: Long): Set<PlayerBestia> {
    val bestias = playerBestiaDao.findPlayerBestiasForAccount(accId).toMutableSet()
    // Add master as well since its not listed as a "player bestia".
    bestias.add(getMaster(accId))

    return bestias
  }

  /**
   * Returns the player bestia with the given id or null.
   *
   * @param playerBestiaId
   * @return
   */
  fun getPlayerBestia(playerBestiaId: Long): PlayerBestia {
    return playerBestiaDao.findOneOrThrow(playerBestiaId)
  }

  /**
   * Returns the master bestia for this given account id.
   *
   * @param accountId
   * @return The master bestia or NULL if the account does not extist.
   */
  fun getMaster(accountId: Long): PlayerBestia {
    return playerBestiaDao.findMasterBestiasForAccount(accountId)
  }

  /**
   * Saves the given player bestia into the database.
   *
   * @param playerBestia The bestia to save into the database.
   */
  fun save(playerBestia: PlayerBestia) {
    Objects.requireNonNull(playerBestia)

    LOG.debug("Persisting player bestia: {}.", playerBestia)
    playerBestiaDao.save(playerBestia)
  }

  companion object {
    private const val NUM_ITEM_SLOTS = 5
  }
}
