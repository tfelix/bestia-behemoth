package net.bestia.zone.casting.plagiarism

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.skillIdsOf
import net.bestia.zone.skill.tree.MasterSkillTreeNode
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Random

class PlagiarismTest {

  private val world = testWorld()
  private val skillIds = skillIdsOf(SkillId.M_PLAGIARISM to PLAGIARISM)
  private val tree = MasterSkillTreeRegistry().apply {
    load(listOf(MasterSkillTreeNode(skillId = FIRE_BOLT, identifier = "M_FIRE_BOLT", maxLevel = 10, tree = "SCHOLAR")))
  }
  private val bestias = mockk<BestiaCatalogue> {
    every { attackLevelOf(EMBER) } returns 10
    every { attackLevelOf(METEOR) } returns 70
    every { attackLevelOf(FIRE_BOLT) } returns null
  }
  private val push = mockk<SkillListPush>(relaxed = true)
  private val foe = 99L

  private fun plagiarism(random: Random = FixedRandom(0f)) = Plagiarism(skillIds, tree, bestias, push, random)

  @Test
  fun `a master spell above the cap is copied at the cap, and the player is told`() {
    val assassin = assassin(plagiarismLevel = 2)

    plagiarism().onStruck(world, assassin, foe, FIRE_BOLT, skillLevel = 5)

    assertEquals(FIRE_BOLT to 4, copyOf(assassin))
    assertEquals(4, plagiarism().grantedLevel(world, assassin, FIRE_BOLT))
    verify { push.push(world, assassin) }
  }

  @Test
  fun `a bestia attack is copied up to the attack level cap and no higher`() {
    val assassin = assassin(plagiarismLevel = 3)

    plagiarism().onStruck(world, assassin, foe, METEOR, skillLevel = 1)
    assertNull(copyOf(assassin), "attack level 70 is above the cap of 60")

    plagiarism().onStruck(world, assassin, foe, EMBER, skillLevel = 2)
    assertEquals(EMBER to 2, copyOf(assassin))
  }

  @Test
  fun `the copy chance is 10 percent a level`() {
    val assassin = assassin(plagiarismLevel = 1)

    val missesEveryRoll = object : Random() {
      override fun nextInt(bound: Int): Int = bound - 1
    }
    plagiarism(missesEveryRoll).onStruck(world, assassin, foe, EMBER, skillLevel = 1)

    assertNull(copyOf(assassin))
    assertEquals(listOf(10, 20, 30, 40, 50), (1..5).map { Plagiarism.chancePercent(it) })
  }

  @Test
  fun `below Lv 5 a new copy replaces the old one, at Lv 5 the old one stays`() {
    val adept = assassin(plagiarismLevel = 4)
    val master = assassin(plagiarismLevel = 5)

    listOf(adept, master).forEach { plagiarism().onStruck(world, it, foe, EMBER, skillLevel = 1) }
    listOf(adept, master).forEach { plagiarism().onStruck(world, it, foe, FIRE_BOLT, skillLevel = 9) }

    assertEquals(FIRE_BOLT to 8, copyOf(adept))
    assertEquals(EMBER to 1, copyOf(master))
  }

  @Test
  fun `a master without Plagiarism copies nothing, and nobody copies their own spell`() {
    val plain = assassin(plagiarismLevel = 0)
    val assassin = assassin(plagiarismLevel = 5)

    plagiarism().onStruck(world, plain, foe, EMBER, skillLevel = 1)
    plagiarism().onStruck(world, assassin, assassin, EMBER, skillLevel = 1)

    assertNull(copyOf(plain))
    assertNull(copyOf(assassin))
  }

  @Test
  fun `casting the copy uses it up, casting a learned level does not`() {
    val assassin = assassin(plagiarismLevel = 5, FIRE_BOLT to 2)
    world.add(assassin, PlagiarizedSkill(FIRE_BOLT, 6))

    plagiarism().onCast(world, assassin, FIRE_BOLT, skillLevel = 2)
    assertEquals(FIRE_BOLT to 6, copyOf(assassin))

    plagiarism().onCast(world, assassin, FIRE_BOLT, skillLevel = 6)
    assertFalse(world.has(assassin, PlagiarizedSkill::class))
  }

  private fun assassin(plagiarismLevel: Int, vararg learned: Pair<Long, Int>): EntityId {
    return world.createEntity { id ->
      add(id, KnownSkills(mutableMapOf(PLAGIARISM to plagiarismLevel, *learned)))
    }
  }

  private fun copyOf(entityId: EntityId): Pair<Long, Int>? {
    return world.get(entityId, PlagiarizedSkill::class)?.let { it.skillId to it.level }
  }

  private companion object {
    const val PLAGIARISM = 126L
    const val FIRE_BOLT = 40L
    const val EMBER = 1000L
    const val METEOR = 1001L
  }
}
