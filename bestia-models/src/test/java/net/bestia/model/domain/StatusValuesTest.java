package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;

public class StatusValuesTest {

	@Test
	public void set_notNull_correctValues() {

		ConditionValues vals = new ConditionValues();
		ConditionValues rhs = new ConditionValues();
		rhs.setMaxHealth(200);
		rhs.setMaxMana(200);
		rhs.setCurrentHealth(123);
		rhs.setCurrentMana(100);
		vals.set(rhs);

		Assert.assertEquals(123, vals.getCurrentHealth());
		Assert.assertEquals(100, vals.getCurrentMana());

	}

	@Test(expected = NullPointerException.class)
	public void set_null_throws() {

		ConditionValues vals = new ConditionValues();
		vals.set(null);

	}

	@Test
	public void addHealth_negative_nonNegative() {
		ConditionValues sv = new ConditionValues();
		sv.setCurrentHealth(10);
		sv.addHealth(-100);
		Assert.assertEquals(0, sv.getCurrentHealth());
	}

	@Test
	public void addHealth_positive_correct() {
		ConditionValues sv = new ConditionValues();
		sv.setMaxHealth(18);
		sv.setCurrentHealth(10);
		sv.addHealth(10);
		Assert.assertEquals(18, sv.getCurrentHealth());
	}

	@Test
	public void addMana_negative_nonNegative() {
		ConditionValues sv = new ConditionValues();
		sv.setCurrentMana(10);
		sv.addMana(-100);
		Assert.assertEquals(0, sv.getCurrentMana());
	}

	@Test
	public void addMana_positive_correct() {
		ConditionValues sv = new ConditionValues();
		sv.setMaxMana(18);
		sv.setCurrentMana(10);
		sv.addMana(10);
		Assert.assertEquals(18, sv.getCurrentMana());
	}
}
