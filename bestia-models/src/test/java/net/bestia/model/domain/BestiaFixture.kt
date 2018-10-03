package net.bestia.model.domain

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
      statusPoints = StatusPointsImpl(),
      baseValues = BaseValues.nullValues
  )
}