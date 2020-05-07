package net.bestia.loginserver.account

import net.bestia.loginserver.error.BadRequestException
import net.bestia.loginserver.error.BestiaException
import net.bestia.loginserver.error.BestiaExceptionCode
import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.bestia.PlayerBestia
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOneOrThrow
import net.bestia.model.item.ItemRepository
import net.bestia.model.item.PlayerItem
import net.bestia.model.item.PlayerItemRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class AccountCreateService(
    private val accountRepository: AccountRepository,
    private val bestiaRepository: BestiaRepository,
    private val itemRepository: ItemRepository,
    private val playerItemRepository: PlayerItemRepository,
    private val playerBestiaRepository: PlayerBestiaRepository
) {

  fun createAccount(newAccount: AccountCreateModel) {
    val account = Account(
        username = newAccount.username,
        gender = newAccount.gender,
        hairstyle = newAccount.hairstyle
    )

    val masterBestiaId = when (newAccount.playerMaster) {
      0 -> 1L
      else -> throw BadRequestException
    }

    val origin = bestiaRepository.findOneOrThrow(masterBestiaId)
    val master = PlayerBestia(
        owner = account,
        master = account,
        origin = origin
    )

    // TODO Optionally alter the bestias and setup some items as well.
    val playerItems = listOf(
        Pair("apple", 10)
    ).mapNotNull {
      val item = itemRepository.findItemByName(it.first) ?: return@mapNotNull null

      PlayerItem(account = account, item = item, amount = it.second)
    }

    try {
      accountRepository.save(account)
    } catch (e: DataIntegrityViolationException) {
      throw BestiaException(BestiaExceptionCode.ACCOUNT_REGISTER_USERNAME_IN_USE)
    }

    playerBestiaRepository.save(master)
    playerItemRepository.saveAll(playerItems)
  }
}