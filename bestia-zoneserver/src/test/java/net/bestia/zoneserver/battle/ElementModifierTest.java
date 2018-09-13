package net.bestia.zoneserver.battle;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.model.domain.Element;

public class ElementModifierTest {

	@Test
	public void getModifier_differentTests() {

		Assert.assertEquals(100, ElementModifier.INSTANCE.getModifier(Element.EARTH, Element.EARTH));
		Assert.assertEquals(0, ElementModifier.INSTANCE.getModifier(Element.FIRE, Element.WATER_3));
		Assert.assertEquals(100, ElementModifier.INSTANCE.getModifier(Element.POISON, Element.NORMAL_2));
		Assert.assertEquals(0, ElementModifier.INSTANCE.getModifier(Element.GHOST, Element.NORMAL_2));
		Assert.assertEquals(-75, ElementModifier.INSTANCE.getModifier(Element.SHADOW, Element.UNDEAD_3));
	}

	@Test(expected = IllegalArgumentException.class)
	public void getModifier_nonLv1AttackElement_throws() {
		ElementModifier.INSTANCE.getModifier(Element.EARTH_3, Element.FIRE_2);
	}
}
