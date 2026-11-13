package net.bestia.zone.item.mining

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.persistence.SkillRepository
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RichVeinsTest {

  private val skills = mockk<SkillRepository> {
    every { findByIdentifier(SkillId.M_RICH_VEINS.name) } returns mockk<Skill> { every { id } returns RICH_VEINS_ID }
  }

  /** Always rolls just under 50%, so Lv.5 always pays and Lv.4 never does. */
  private val rolls = mockk<Random> { every { nextDouble() } returns 0.45 }

  private val sut = RichVeins(skills, rolls)
  private val world = testWorld()

  private val iron = OreYield.Stack(IRON_ORE, 1)
  private val gold = OreYield.Stack(GOLD_ORE, 1, isCoinMetal = true)

  @Test
  fun `a master with enough Rich Veins gets one more piece`() {
    val master = masterWith(level = 5)

    assertEquals(1, sut.extraPieces(world, master, iron))
  }

  @Test
  fun `the chance grows by a tenth per level`() {
    val master = masterWith(level = 4)

    assertEquals(0, sut.extraPieces(world, master, iron))
  }

  @Test
  fun `gold never drops more, because it sizes the coin supply`() {
    val master = masterWith(level = 5)

    assertEquals(0, sut.extraPieces(world, master, gold))
  }

  @Test
  fun `a bestia at its master's shaft gets the master's Rich Veins`() {
    masterWith(level = 5)
    val bestia = world.createEntity { add(it, OwnedBestia(masterId = MASTER_ID, playerBestiaId = 11L)) }

    assertEquals(1, sut.extraPieces(world, bestia, iron))
  }

  private fun masterWith(level: Int) = world.createEntity {
    add(it, Master(MASTER_ID))
    add(it, KnownSkills(mutableMapOf(RICH_VEINS_ID to level)))
  }

  private companion object {
    const val RICH_VEINS_ID = 42L
    const val MASTER_ID = 7L
    const val IRON_ORE = 8L
    const val GOLD_ORE = 31L
  }
}
