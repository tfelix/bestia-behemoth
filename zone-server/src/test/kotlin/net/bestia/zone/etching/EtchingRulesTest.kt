package net.bestia.zone.etching

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.essence.EssenceDefinition
import net.bestia.zone.item.essence.EssenceEffect
import net.bestia.zone.item.essence.GemTier
import net.bestia.zone.item.essence.SocketGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EtchingRulesTest {

  private val catalogue = mockk<BestiaCatalogue> {
    every { find(BOAR) } returns species(BOAR, "boar", EssenceDefinition(SocketGroup.WEAPON, EssenceEffect.BANE, "BEAST"))
    every { find(CRITTER) } returns species(CRITTER, "critter", essence = null)
    every { find(BLOB_QUEEN) } returns species(
      BLOB_QUEEN, "blob_queen", EssenceDefinition(SocketGroup.ARMOR, EssenceEffect.BOSS, "ROYAL_JELLY"), bossLevel = 42
    )
  }

  private val itemTemplates = mockk<ItemTemplateRegistry> {
    every { identifierOf(CUT_RUBY) } returns "cut_ruby"
  }

  private val rules = EtchingRules(catalogue, itemTemplates)

  @Test
  fun `a cut gem names its tier`() {
    assertEquals(GemTier.RUBY, rules.tierOfGem(CUT_RUBY))
  }

  @Test
  fun `a Bestia fills a gem once it reached the gem's level`() {
    assertTrue(rules.fits(BOAR, 6, GemTier.AMETHYST))
    assertFalse(rules.fits(BOAR, 40, GemTier.RUBY))
    assertTrue(rules.fits(BOAR, 41, GemTier.RUBY))
  }

  @Test
  fun `a species without an essence fills no gem`() {
    assertFalse(rules.fits(CRITTER, 6, GemTier.AMETHYST))
  }

  @Test
  fun `a boss fills only the gem of its level`() {
    assertTrue(rules.fits(BLOB_QUEEN, 42, GemTier.RUBY))
    assertFalse(rules.fits(BLOB_QUEEN, 42, GemTier.AMETHYST))
    assertFalse(rules.fits(BLOB_QUEEN, 42, GemTier.PYRELITH))
  }

  private fun species(id: Long, identifier: String, essence: EssenceDefinition?, bossLevel: Int? = null): Bestia {
    val level = bossLevel ?: 1
    return Bestia(
      id = id, identifier = identifier, levelMin = level, levelMax = level, experienceReward = 0, health = 1, mana = 1,
      essence = essence, boss = bossLevel != null
    )
  }

  private companion object {
    const val BOAR = 6L
    const val CRITTER = 98L
    const val BLOB_QUEEN = 79L
    const val CUT_RUBY = 138L
  }
}
