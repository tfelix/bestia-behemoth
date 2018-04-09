package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.InventoryComponent
import net.bestia.entity.component.LevelComponent
import net.bestia.entity.component.StatusComponent
import net.bestia.model.dao.AccountDAO
import net.bestia.model.dao.ItemDAO
import net.bestia.model.dao.PlayerItemDAO
import net.bestia.model.domain.Item
import net.bestia.model.domain.PlayerItem
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
        private val accountDao: AccountDAO,
        private val itemDao: ItemDAO,
        private val entityService: EntityService) {

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
    val invComp = checkAndGetInventoryComp(entity)

    // Now we must check if we have a status component.
    val statusComp = entityService.getComponent(entity, StatusComponent::class.java)

    if (!statusComp.isPresent) {
      invComp.maxWeight = 0f
      entityService.updateComponent(invComp)
      return
    }

    // Currently we can not distinguish between bestia classes.
    val level = entityService
            .getComponent(entity, LevelComponent::class.java)
            .map { it.level }
            .orElse(1)

    val str = statusComp.map { c -> c.statusPoints.strength }.orElse(0)
    val maxWeight = BASE_WEIGHT + str * 4 * level

    LOG.trace("Setting max weight for entity {} to {}.", entity, maxWeight)

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
    val invComp = checkAndGetInventoryComp(entity)
    invComp.maxItemCount = maxNumItems
    entityService.updateComponent(invComp)
  }

  /**
   * Checks if the entity has the inventory component, if not the component
   * will be added.
   */
  private fun checkAndGetInventoryComp(entity: Entity): InventoryComponent {
    val invCompOpt = entityService.getComponent(entity, InventoryComponent::class.java)
    val invComp: InventoryComponent
    if (!invCompOpt.isPresent) {
      invComp = entityService.newComponent(InventoryComponent::class.java)
      entityService.attachComponent(entity, invComp)
    } else {
      invComp = invCompOpt.get()
    }

    return invComp
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
    var pitem: PlayerItem? = playerItemDao.findPlayerItem(accId, itemId)

    if (pitem == null) {
      // New item.

      val acc = accountDao.findOne(accId)
      val item = itemDao.findOne(itemId)

      if (acc == null) {
        LOG.info("Could not find account {}", accId)
        return false
      }

      if (item == null) {
        LOG.info("Could not find item {}", itemId)
        return false
      }

      pitem = PlayerItem(item, acc, amount)
      playerItemDao.save(pitem)
    } else {
      // Update existing item.
      pitem.amount = pitem.amount + amount
      playerItemDao.save(pitem)
    }

    LOG.info("Account {} received item {}, amount: {}", accId, itemId, amount)

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
    return playerItemDao.findOne(playerItemId)
  }

  /**
   * Returns a [PlayerItem] with the given account id and item id. If
   * the item does not exist in the inventory for this given account, null is
   * returned.
   *
   * @param accId
   * Account ID
   * @param itemId
   * Item ID
   * @return The [PlayerItem] or NULL if the item does not exist.
   */
  fun getPlayerItem(accId: Long, itemId: Int): PlayerItem {
    return playerItemDao.findPlayerItem(accId, itemId)
  }

  /**
   * Returns a [Item] with the given item db name or NULL if no item
   * with this name was found.
   *
   * @param itemDbName
   * The item database name.
   * @return The [Item] or NULL.
   */
  fun getItem(itemDbName: String): Item {
    return itemDao.findItemByName(itemDbName)
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
    val item = playerItemDao.findOne(playerItemId)

    return item != null && item.amount >= amount

  }

  companion object {
    private const val BASE_WEIGHT = 150f
  }
}
