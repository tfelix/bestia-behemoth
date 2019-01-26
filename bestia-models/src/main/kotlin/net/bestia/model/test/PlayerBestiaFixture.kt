package net.bestia.model.test

import net.bestia.model.bestia.PlayerBestia

object PlayerBestiaFixture {
  val playerBestiaWithoutMaster = PlayerBestia(
      owner = AccountFixture.createAccount(),
      origin = BestiaFixture.bestia
  )
}