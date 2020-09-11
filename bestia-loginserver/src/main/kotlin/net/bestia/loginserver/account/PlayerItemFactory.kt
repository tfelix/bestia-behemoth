package net.bestia.loginserver.account

import mu.KotlinLogging
import net.bestia.model.account.Account
import net.bestia.model.item.ItemRepository
import net.bestia.model.item.PlayerItem
import net.bestia.model.item.PlayerItemRepository
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
class PlayerItemFactory(
    private val itemRepository: ItemRepository,
    private val playerItemRepository: PlayerItemRepository
) {

  // ITEM_DB_NAME and amount
  private val starterItems = listOf(
      Pair("apple", 10)
  )

  fun addStarterItems(account: Account) {
    LOG.debug { "Adding items: $starterItems to $account." }

    val playerItems = starterItems.mapNotNull {
      itemRepository.findItemByName(it.first)?.let { item ->
        Pair(item, it.second)
      }
    }.map {
      PlayerItem(account = account, item = it.first, amount = it.second)
    }

    playerItemRepository.saveAll(playerItems)
  }
}