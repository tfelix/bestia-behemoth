package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConditionValuesTest {
	
	private ConditionValues cond;
	
	@Before
	public void setup() {
		cond = new ConditionValues();
	}
	
	@Test
	public void setCurrentHealth_biggerThenMaxHealth_maxHealth() {
		cond.setMaxHealth(100);
		cond.setCurrentHealth(110);
		Assert.assertEquals(100, cond.getCurrentHealth());
	}
	
	@Test
	public void setCurrentMana_biggerThenMaxMana_maxMana() {
		cond.setMaxMana(100);
		cond.setCurrentMana(110);
		Assert.assertEquals(100, cond.getCurrentMana());
	}
	
	@Test
	public void setCurrentMana_tooLowMana_setToMinimum() {
		cond.setMaxMana(100);
		cond.setCurrentMana(-10);
		Assert.assertEquals(0, cond.getCurrentMana());
	}
	
	@Test
	public void setCurrentHealth_tooLowHealth_setToMinimum() {
		cond.setMaxHealth(100);
		cond.setCurrentHealth(-10);
		Assert.assertEquals(0, cond.getCurrentHealth());
	}

}
