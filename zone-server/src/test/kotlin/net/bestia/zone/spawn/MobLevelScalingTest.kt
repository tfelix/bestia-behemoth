package net.bestia.zone.spawn

import net.bestia.zone.bestia.persistence.Bestia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobLevelScalingTest {

  private val sut = MobLevelScaling(MobLevelConfig(scalingOffset = 10))

  /** Middle of 1..3 is 2, so a level 3 individual sits one above the level the numbers are fitted for. */
  private val rat = Bestia(
    id = 1, identifier = "rat", levelMin = 1, levelMax = 3, experienceReward = 6, health = 96, mana = 30,
    strength = 5, vitality = 10, intelligence = 1, dexterity = 4, willpower = 20, agility = 8
  )

  @Test
  fun `the middle of the range gets the authored numbers`() {
    assertEquals(2, rat.referenceLevel)

    assertEquals(1.0, sut.factor(2, rat))
    assertEquals(96, sut.health(2, rat))
    assertEquals(30, sut.mana(2, rat))
    assertEquals(6, sut.experience(2, rat))
    assertEquals(5, sut.attributes(2, rat).strength)
    assertEquals(20, sut.attributes(2, rat).willpower)
  }

  @Test
  fun `a higher roll is stronger and worth more than a lower one`() {
    assertTrue(sut.health(3, rat) > sut.health(2, rat))
    assertTrue(sut.health(2, rat) > sut.health(1, rat))
    assertTrue(sut.experience(3, rat) > sut.experience(1, rat))
    assertTrue(sut.attributes(3, rat).willpower > sut.attributes(1, rat).willpower)
  }

  @Test
  fun `the factor is the offset ratio`() {
    assertEquals(13.0 / 12.0, sut.factor(3, rat))
    assertEquals(104, sut.health(3, rat))
  }

  @Test
  fun `a high bracket moves a few percent per level`() {
    val ogre = Bestia(id = 2, identifier = "ogre", levelMin = 50, levelMax = 58, experienceReward = 300, health = 1300, mana = 160)

    assertEquals(54, ogre.referenceLevel)
    assertEquals(1300, sut.health(54, ogre))
    assertEquals(1219, sut.health(50, ogre))
    assertEquals(1381, sut.health(58, ogre))
  }

  @Test
  fun `an attribute never scales below one`() {
    val weak = Bestia(
      id = 3, identifier = "weak", levelMin = 1, levelMax = 30, experienceReward = 1, health = 1, mana = 0,
      strength = 1, vitality = 1, intelligence = 1, dexterity = 1, willpower = 1, agility = 1
    )

    assertEquals(1, sut.attributes(1, weak).strength)
    assertEquals(1, sut.health(1, weak))
    assertEquals(0, sut.mana(1, weak))
  }

  @Test
  fun `an offset of zero is proportional to level`() {
    val proportional = MobLevelScaling(MobLevelConfig(scalingOffset = 0))

    assertEquals(1.5, proportional.factor(3, rat))
  }
}
