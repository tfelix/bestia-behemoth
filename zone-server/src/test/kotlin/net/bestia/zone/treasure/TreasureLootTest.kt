package net.bestia.zone.treasure

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.treasure.TreasureTier
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TreasureLootTest {

  private val loot = TreasureLoot(
    reserve = mockk(relaxed = true),
    money = mockk(relaxed = true),
    commodities = mockk { every { coinItemId() } returns null },
    items = mockk { every { idOf(any()) } answers { ITEM_IDS[firstArg()] } },
    stats = mockk { every { withRandomStats() } returns emptyList() },
    roller = mockk(),
    random = Random(1L),
  )

  @Test
  fun `a cache on a young battlefield holds what the dead dropped`() {
    repeat(20) {
      val contents = loot.roll(TreasureTier.MODEST, excavationLevel = 0, battlefieldAge = 0.1)

      assertTrue(contents.any { it.itemId == SCRAP }, "no scrap in $contents")
    }
  }

  @Test
  fun `an old battlefield and any other site hold no battle junk`() {
    for (age in listOf(0.8, Double.NaN)) {
      repeat(20) {
        val contents = loot.roll(TreasureTier.MODEST, excavationLevel = 0, battlefieldAge = age)

        assertFalse(contents.any { it.itemId == SCRAP || it.itemId == BONE }, "junk at age $age: $contents")
      }
    }
  }

  private companion object {
    const val SCRAP = 341L
    const val BONE = 92L

    val ITEM_IDS = mapOf("rusted_scrap" to SCRAP, "bone_shard" to BONE, "rough_gem" to 60L, "sea_pearl" to 61L)
  }
}
