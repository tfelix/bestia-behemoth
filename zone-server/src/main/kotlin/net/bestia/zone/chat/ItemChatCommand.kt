package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.item.inventory.InventoryItemFactory
import net.bestia.zone.item.ItemRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * Spawns items.
 */
@Component
class ItemChatCommand(
  private val itemRepository: ItemRepository,
  private val inventoryItemFactory: InventoryItemFactory,
  private val connectionInfoService: ConnectionInfoService
) : ChatCommand() {

  companion object {
    private val LOG = KotlinLogging.logger { }
    private val CMD_REGEX = Regex("""^/item\s+(\S+)\s+(\d+)$""")
  }

  override fun getHelpText(): String {
    return "/item <ITEM_ID | ITEM_DB_NAME> <AMOUNT> - Spawns an item for the command user or the given player id."
  }

  override val requiredAuthority: Authority = Authority.ITEM

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false

    val itemArg = match.groupValues[1]
    val amount = match.groupValues[2].toInt()

    val activeEntityId = connectionInfoService.getActiveEntityId(playerId)
    val masterId = connectionInfoService.getMasterId(playerId)

    val item = itemArg.toLongOrNull()
      ?.let { itemRepository.findByIdOrNull(it) }
      ?: itemRepository.findByIdentifier(itemArg)

    if (item == null) {
      LOG.warn { "Item command failed: item '$itemArg' not found" }
      return false
    }

    inventoryItemFactory.addItemToMasterAndEntity(masterId, activeEntityId, item.identifier, amount)
    LOG.info { "Added ${amount}x ${item.identifier} to master $masterId (active entity $activeEntityId)" }

    return true
  }
}
