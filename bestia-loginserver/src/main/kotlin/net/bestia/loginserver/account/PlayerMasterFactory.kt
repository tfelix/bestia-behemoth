package net.bestia.loginserver.account

import net.bestia.loginserver.error.BadRequestException
import net.bestia.model.account.Account
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.bestia.PlayerBestia
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOneOrThrow
import org.springframework.stereotype.Component

@Component
class PlayerMasterFactory(
    private val bestiaRepository: BestiaRepository,
    private val playerBestiaRepository: PlayerBestiaRepository
) {

  private val masterToBestiaId = mapOf(
      0 to 1L
  )

  fun createBestiaMaster(account: Account, newAccount: AccountCreateModel) {
    val masterBestiaId = masterToBestiaId[newAccount.playerMaster]
        ?: throw BadRequestException

    val origin = bestiaRepository.findOneOrThrow(masterBestiaId)
    val master = PlayerBestia(
        owner = account,
        master = account,
        origin = origin
    )

    playerBestiaRepository.save(master)
  }
}