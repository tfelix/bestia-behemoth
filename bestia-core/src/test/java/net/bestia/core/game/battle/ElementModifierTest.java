package net.bestia.core.game.battle;

import org.junit.*;


public class ElementModifierTest {
	@Test
	public void controlsample_modifier_test() {
		double result = ElementModifier.getModifier(Element.FIRE, Element.WATER);
		Assert.assertEquals("No correct element modifier.", 0.5, result, 0.000001);
		result = ElementModifier.getModifier(Element.HOLY, Element.UNDEAD);
		Assert.assertEquals("No correct element modifier.", 1.5, result, 0.000001);
		result = ElementModifier.getModifier(Element.NORMAL, Element.NORMAL);
		Assert.assertEquals("No correct element modifier.", 1.0, result, 0.000001);
		// TODO weitere hinzufügen.
	}
}
