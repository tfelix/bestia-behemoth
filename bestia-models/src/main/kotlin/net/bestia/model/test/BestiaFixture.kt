package net.bestia.model.test

import net.bestia.model.battle.Element
import net.bestia.model.bestia.BaseValues
import net.bestia.model.bestia.Bestia
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.bestia.BestiaType

object BestiaFixture {

  @JvmStatic
  fun createBestia(bestiaRepository: BestiaRepository): Bestia {
    return Bestia(
        databaseName = "testBestia",
        defaultName = "Test",
        element = Element.NORMAL,
        mesh = "test",
        expGained = 100,
        type = BestiaType.DEMI_HUMAN,
        level = 10,
        baseValues = BaseValues.NULL_VALUES
    ).also { bestiaRepository.save(it) }
  }
}