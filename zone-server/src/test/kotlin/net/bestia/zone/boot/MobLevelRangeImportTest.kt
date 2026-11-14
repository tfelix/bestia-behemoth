package net.bestia.zone.boot

import net.bestia.zone.battle.BestiaKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MobLevelRangeImportTest {

  private fun dto(min: Int, max: Int): MobImporterBootRunner.MobYmlDto {
    return MobImporterBootRunner.MobYmlDto(
      id = 1, identifier = "rat", levelMin = min, levelMax = max, health = 10, mana = 5, experience = 4,
      loot = emptyList(), kind = BestiaKind.BEAST, name = "Rat"
    )
  }

  @Test
  fun `a range is read as authored`() {
    val rat = dto(1, 3)

    assertEquals(1, rat.levelMin)
    assertEquals(3, rat.levelMax)
  }

  @Test
  fun `a species may be one level`() {
    assertEquals(dto(80, 80).levelMin, dto(80, 80).levelMax)
  }

  @Test
  fun `a minimum above the maximum is refused`() {
    assertThrows<IllegalArgumentException> { dto(5, 3) }
  }

  @Test
  fun `a level below one is refused`() {
    assertThrows<IllegalArgumentException> { dto(0, 3) }
  }
}
