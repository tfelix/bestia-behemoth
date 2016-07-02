package net.bestia.model.misc;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.model.misc.Damage.DamageType;

public class DamageTest {

	private static final String uuid1 = "1235-1234-1325-1245";

	@Test
	public void getHit_ok() {
		Damage.getHit(uuid1, 100);
	}

	@Test(expected = IllegalArgumentException.class)
	public void getHit_negAmount_throw() {
		Damage.getHit(uuid1, -100);
	}

	@Test
	public void getHeal_ok() {
		Damage.getHeal(uuid1, 100);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void getHeal_negAmount_throw() {
		Damage.getHeal(uuid1, -100);
	}

	@Test
	public void getMiss_ok() {
		Damage.getMiss(uuid1);
	}

	@Test
	public void setEntityUUID_ok() {
		final Damage d = new Damage(uuid1, 10, DamageType.HEAL);
		final String uuid2 = "bla bla bla";
		d.setEntityUUID(uuid2);
		Assert.assertEquals(uuid2, d.getEntityUUID());
	}

	@Test(expected = IllegalArgumentException.class)
	public void setEntityUUID_null_throw() {
		final Damage d = new Damage(uuid1, 10, DamageType.HEAL);
		d.setEntityUUID(null);
	}

	@Test
	public void setDamage_ok() {
		final Damage d = new Damage(uuid1, 10, DamageType.HEAL);
		int dmg = 1337;
		d.setDamage(dmg);
		Assert.assertEquals(dmg, d.getDamage());
	}

	@Test(expected=IllegalArgumentException.class)
	public void setDamage_negative_throw() {
		final Damage d = new Damage(uuid1, 10, DamageType.HEAL);
		int dmg = -1337;
		d.setDamage(dmg);
	}
}
