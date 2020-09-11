package net.bestia.model.test

import net.bestia.model.account.AccountRepository
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.bestia.PlayerBestia
import net.bestia.model.bestia.PlayerBestiaRepository

object PlayerBestiaFixture {

  fun createPlayerBestiaWithoutMaster(
      playerBestiaRepository: PlayerBestiaRepository,
      accountRepository: AccountRepository,
      bestiaRepository: BestiaRepository
  ): PlayerBestia {
    return PlayerBestia(
        owner = AccountFixture.createAccount(accountRepository),
        origin = BestiaFixture.createBestia(bestiaRepository)
    ).also { playerBestiaRepository.save(it) }
  }
}