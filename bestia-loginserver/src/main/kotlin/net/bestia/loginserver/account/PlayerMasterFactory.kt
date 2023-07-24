package net.bestia.loginserver.account

import net.bestia.loginserver.error.BadRequestException
import net.bestia.model.account.Account
import net.bestia.model.bestia.BaseValues.Companion.STARTER_IV_VALUES
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

  private val masterToBestiaIdMap = mapOf(
      0 to 1L
  )

  fun createBestiaMaster(account: Account, newAccount: CreateAccountRequestV1) {
    val masterBestiaId = masterToBestiaIdMap[newAccount.playerMasterIndex]
        ?: throw BadRequestException

    val origin = bestiaRepository.findOneOrThrow(masterBestiaId)

    val master = PlayerBestia(
        owner = account,
        master = account,
        origin = origin
    )
    master.individualValue = STARTER_IV_VALUES

    // TODO Determine the starting location of the Bestia. During map creation there should be 3-4 starting
    //   locations randomly chosen and persisted.

    playerBestiaRepository.save(master)
  }
}