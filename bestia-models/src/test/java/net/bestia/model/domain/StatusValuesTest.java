package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;

public class StatusValuesTest {

	@Test
	public void set_notNull_correctValues() {

		StatusValues vals = new StatusValues();
		StatusValues rhs = new StatusValues();
		rhs.setCurrentHealth(123);
		rhs.setCurrentMana(100);
		vals.set(rhs);

		Assert.assertEquals(123, vals.getCurrentHealth());
		Assert.assertEquals(100, vals.getCurrentMana());

	}

	@Test(expected = NullPointerException.class)
	public void set_null_throws() {

		StatusValues vals = new StatusValues();
		vals.set(null);

	}

	@Test
	public void addHealth_negative_nonNegative() {
		StatusValues sv = new StatusValues();
		sv.setCurrentHealth(10);
		sv.addHealth(-100);
		Assert.assertEquals(0, sv.getCurrentHealth());
	}

	@Test
	public void addHealth_positive_correct() {
		StatusValues sv = new StatusValues();
		sv.setCurrentHealth(10);
		sv.addHealth(10);
		Assert.assertEquals(20, sv.getCurrentHealth());
	}

	@Test
	public void addMana_negative_nonNegative() {
		StatusValues sv = new StatusValues();
		sv.setCurrentMana(10);
		sv.addMana(-100);
		Assert.assertEquals(0, sv.getCurrentMana());
	}

	@Test
	public void addMana_positive_correct() {
		StatusValues sv = new StatusValues();
		sv.setCurrentMana(10);
		sv.addMana(10);
		Assert.assertEquals(20, sv.getCurrentMana());
	}
}
