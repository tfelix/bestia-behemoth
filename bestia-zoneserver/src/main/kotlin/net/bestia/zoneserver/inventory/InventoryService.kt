package net.bestia.zoneserver.inventory

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.InventoryComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import net.bestia.model.account.AccountRepository
import net.bestia.model.item.ItemRepository
import net.bestia.model.item.PlayerItemDAO
import net.bestia.model.findOneOrThrow
import net.bestia.model.item.PlayerItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

private val LOG = KotlinLogging.logger { }

/**
 * This service kind of manages the user relationship with the inventory. Since
 * access to the inventory might happen on different server (user can have
 * bestias on multiple servers at the same time) no items can be cached. They
 * must be returned from the server all the time.
 *
 *
 * With the help of this class this is archived. When inventory changes notice
 * messages will be generated which can later be retrieved by the server.
 *
 *
 * @author Thomas Felix
 */
@Transactional(propagation = Propagation.REQUIRED, readOnly = false)
@Service
class InventoryService(
    private val playerItemDao: PlayerItemDAO,
    private val accountDao: AccountRepository,
    private val itemDao: ItemRepository,
    private val entityService: EntityService
) {

  /**
   * If the entity also has a [StatusComponent] attached it will update
   * the max weight setting depending on this status strength. The formula is:
   *
   *
   * Weight_max = 200 + STR * 4 + 3 * Lv
   *
   * If the entity does not have a status component then the weight will be 0.
   * As a guideline one weight unit loosly resembles 0.1 kg. The entity needs
   * a level and a status component.
   *
   * @param entity
   * This entity inventory gets updated with the maximum weight.
   */
  fun updateMaxWeight(entity: Entity) {
    val invComp = entity.getComponent(InventoryComponent::class.java)
    val statusComp = entity.getComponent(StatusComponent::class.java)

    val level = entity.tryGetComponent(LevelComponent::class.java)?.level ?: 1

    val str = statusComp.statusPoints.strength
    val maxWeight = BASE_WEIGHT + str * 4 * level

    LOG.trace { "Setting max weight for entity $entity to $maxWeight." }

    invComp.maxWeight = maxWeight

    entityService.updateComponent(invComp)
  }

  /**
   * The maximum number of items are either limited by weight of by a concrete
   * number of max items. If this number is reached no more items can be added
   * to the inventory. If this is set to another value then
   * [InventoryComponent.UNLIMITED_ITEMS] then the weight is ignored.
   *
   * @param entity
   * The entity to update the max num item component.
   * @param maxNumItems
   * The maximum number of items which can be attached to this
   * inventory component.
   */
  fun setMaxItemCount(entity: Entity, maxNumItems: Int) {
    val invComp = entity.getComponent(InventoryComponent::class.java)
    invComp.maxItemCount = maxNumItems

    entityService.updateComponent(invComp)
  }

  /**
   * Checks if an account owns a certain item.
   *
   * @param accId
   * Account ID.
   * @param itemId
   * ID of the item to check.
   * @param amount
   * Amount of the item the player should own.
   * @return TRUE if the item with the given amount is in the inventory. FALSE
   * otherwise.
   */
  fun hasItem(accId: Long, itemId: Int, amount: Int): Boolean {
    val item = playerItemDao.findPlayerItem(accId, itemId)
    return item != null && item.amount >= amount
  }

  /**
   * Checks if an account owns a certain item.
   *
   * @param accId
   * Account ID.
   * @param itemDbName
   * Name of the item to check.
   * @param amount
   * Amount of the item the player should own.
   * @return TRUE if the item with the given amount is in the inventory. FALSE
   * otherwise.
   */
  fun hasItem(accId: Long, itemDbName: String, amount: Int): Boolean {
    val item = itemDao.findItemByName(itemDbName)
    return item != null && hasItem(accId, item.id, amount)
  }

  /**
   * Adds an item to the account.
   *
   */
  fun addItem(accId: Long, itemId: Int, amount: Int): Boolean {

    // Look if the account already has such an item.
    var pitem = playerItemDao.findPlayerItem(accId, itemId)

    if (pitem == null) {
      // New item.
      val acc = accountDao.findOneOrThrow(accId)
      val item = itemDao.findOneOrThrow(itemId)

      if (item == null) {
        LOG.info { "Could not find item $itemId" }
        return false
      }

      pitem = PlayerItem(item, acc, amount)
      playerItemDao.save(pitem)
    } else {
      // Update existing item.
      pitem.amount = pitem.amount + amount
      playerItemDao.save(pitem)
    }

    LOG.info { "Account $accId received item $itemId, amount: $amount" }

    return true
  }

  /**
   * Adds an item to the account. Like addItem.
   *
   */
  fun addItem(accId: Long, itemDbName: String, amount: Int): Boolean {
    val item = itemDao.findItemByName(itemDbName)

    return item != null && addItem(accId, item.id, amount)
  }

  /**
   * Removes an item from the account of this user. The amount must be
   * positive.
   *
   * @param accId Account id.
   * @param itemId Item id.
   * @param amount The amount to be removed. Must be positive.
   * @return TRUE if the item amount was on this account and the item(s) where
   * removed. FALSE if no or not enough items where in this account.
   */
  fun removeItem(accId: Long, itemId: Int, amount: Int): Boolean {
    if (amount < 0) {
      throw IllegalArgumentException("Amount must be positive.")
    }

    val item = playerItemDao.findPlayerItem(accId, itemId) ?: return false

    if (item.amount < amount) {
      return false
    }

    LOG.info("Account {} removed item {}, amount: {}", accId, itemId, amount)

    if (item.amount > amount) {
      item.amount = item.amount - amount
      playerItemDao.save(item)
    } else {
      // can only be equal amounts.
      playerItemDao.delete(item)
    }

    return true
  }

  /**
   * Like removeItem(long, String, int) but accepting the item database name instead of
   * the item id.
   *
   * @param accId
   * Account id.
   * @param itemDbName
   * Item Database name.
   * @param amount
   * Amount to be removed.
   * @return TRUE of the item (and the amount) could be removed. FALSE
   * otherwise.
   */
  fun removeItem(accId: Long, itemDbName: String, amount: Int): Boolean {
    val item = itemDao.findItemByName(itemDbName)

    return item != null && removeItem(accId, item.id, amount)

  }

  /**
   * Returns a [PlayerItem] if the accounts owns this item with the
   * given player item id.
   *
   * @param playerItemId
   * The player item id.
   * @return The [PlayerItem] or NULL if the ID does not exist.
   */
  fun getPlayerItem(playerItemId: Int): PlayerItem {
    return playerItemDao.findOneOrThrow(playerItemId)
  }

  /**
   * Delegates down to the DAO to find all items for the current account.
   * Note: This will and must change when we switch to bestia based
   * inventories.
   */
  fun findPlayerItemsForAccount(accId: Long): List<PlayerItem> {
    return playerItemDao.findPlayerItemsForAccount(accId)
  }

  /**
   * Checks if the user has the given player item with the wanted amount.
   */
  fun hasPlayerItem(playerItemId: Int, amount: Int): Boolean {
    val item = playerItemDao.findOneOrThrow(playerItemId)

    return item.amount >= amount
  }

  companion object {
    private const val BASE_WEIGHT = 150f
  }
}
