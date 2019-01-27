package net.bestia.zoneserver.battle

import net.bestia.model.battle.Element
import org.junit.Assert
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ElementModifierTest {

  @Test
  fun getModifier_differentTests() {
    Assert.assertEquals(100, ElementModifier.getModifier(Element.EARTH, Element.EARTH).toLong())
    Assert.assertEquals(0, ElementModifier.getModifier(Element.FIRE, Element.WATER_3).toLong())
    Assert.assertEquals(100, ElementModifier.getModifier(Element.POISON, Element.NORMAL_2).toLong())
    Assert.assertEquals(0, ElementModifier.getModifier(Element.GHOST, Element.NORMAL_2).toLong())
    Assert.assertEquals(-75, ElementModifier.getModifier(Element.SHADOW, Element.UNDEAD_3).toLong())
  }

  @Test
  fun getModifier_nonLv1AttackElement_throws() {
    assertThrows(IllegalArgumentException::class.java) {
      ElementModifier.getModifier(Element.EARTH_3, Element.FIRE_2)
    }
  }
}
