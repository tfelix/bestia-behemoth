package net.bestia.model.test

import net.bestia.model.battle.Element
import net.bestia.model.bestia.BaseValues
import net.bestia.model.bestia.Bestia
import net.bestia.model.bestia.BestiaType
import net.bestia.model.domain.*

object BestiaFixture {

  val bestia = Bestia(
      databaseName = "testBestia",
      defaultName = "Test",
      element = Element.NORMAL,
      image = "testimage",
      spriteInfo = SpriteInfo("test", VisualType.PACK),
      expGained = 100,
      type = BestiaType.DEMI_HUMAN,
      level = 10,
      baseValues = BaseValues.nullValues
  )
}