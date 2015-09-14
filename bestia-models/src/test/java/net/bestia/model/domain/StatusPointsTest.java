package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;

public class StatusPointsTest {

	@Test
	public void check_invalid_hp() {
		StatusPoints sp = new StatusPoints();

		sp.setMaxHp(100);
		sp.setCurrentHp(0);

		Assert.assertEquals(0, sp.getCurrentHp());
		sp.setCurrentHp(-10);
		Assert.assertEquals(0, sp.getCurrentHp());
		sp.setCurrentHp(110);
		Assert.assertEquals(100, sp.getCurrentHp());
		sp.setMaxHp(90);
		Assert.assertEquals(90, sp.getCurrentHp());
	}

	@Test
	public void check_invalid_mana() {
		StatusPoints sp = new StatusPoints();

		sp.setMaxMana(100);
		sp.setCurrentMana(0);

		Assert.assertEquals(0, sp.getCurrentMana());
		sp.setCurrentMana(-10);
		Assert.assertEquals(0, sp.getCurrentMana());
		sp.setCurrentMana(110);
		Assert.assertEquals(100, sp.getCurrentMana());
		sp.setMaxMana(90);
		Assert.assertEquals(90, sp.getCurrentMana());
	}
	
	@Test
	public void check_invalid_sp_armor() {
		StatusPoints sp = new StatusPoints();

		sp.setArmorSpDef(-10);
		Assert.assertEquals(1, sp.getArmorSpDef());
		sp.setArmorSpDef(10);
		Assert.assertEquals(10, sp.getArmorSpDef());
		sp.setArmorSpDef(110);
		Assert.assertEquals(100, sp.getArmorSpDef());
	}
	
	@Test
	public void check_invalid_armor() {
		StatusPoints sp = new StatusPoints();

		sp.setArmorDef(-10);
		Assert.assertEquals(1, sp.getArmorDef());
		sp.setArmorDef(10);
		Assert.assertEquals(10, sp.getArmorDef());
		sp.setArmorDef(110);
		Assert.assertEquals(100, sp.getArmorDef());
	}

	@Test
	public void check_add() {

		StatusPoints sp1 = new StatusPoints();
		
		sp1.setArmorDef(10);
		sp1.setArmorSpDef(10);
		sp1.setAtk(10);
		sp1.setDef(10);
		
		sp1.setMaxHp(100);
		sp1.setMaxMana(100);
		sp1.setCurrentHp(10);
		sp1.setCurrentMana(10);
		
		sp1.setSpAtk(10);
		sp1.setSpDef(10);
	
		sp1.setSpd(10);
		
		sp1.add(sp1);
		
		Assert.assertEquals(20, sp1.getArmorDef());
		Assert.assertEquals(20, sp1.getArmorSpDef());
		Assert.assertEquals(20, sp1.getAtk());
		Assert.assertEquals(20, sp1.getDef());
		
		Assert.assertEquals(200, sp1.getMaxHp());
		Assert.assertEquals(200, sp1.getMaxMana());
		
		Assert.assertEquals(20, sp1.getCurrentHp());
		Assert.assertEquals(20, sp1.getCurrentMana());
		Assert.assertEquals(20, sp1.getSpAtk());
		Assert.assertEquals(20, sp1.getSpDef());
	}
	
	@Test
	public void test_instanciation() {
		StatusPoints sp = new StatusPoints();
		Assert.assertEquals(0, sp.getAtk());
		Assert.assertEquals(0, sp.getDef());
		Assert.assertEquals(0, sp.getCurrentHp());
		Assert.assertEquals(0, sp.getCurrentMana());
		Assert.assertEquals(0, sp.getSpAtk());
		Assert.assertEquals(0, sp.getSpd());
		Assert.assertEquals(0, sp.getSpDef());
	}
	
	@Test
	public void test_CriticalValues() {
		StatusPoints sp = new StatusPoints();
		sp.setMaxHp(110);
		sp.setCurrentHp(100);
		Assert.assertEquals(100, sp.getCurrentHp());
		sp.setMaxHp(50);
		Assert.assertEquals(50, sp.getCurrentHp());
		
		sp.setMaxMana(200);
		sp.setCurrentMana(100);
		Assert.assertEquals(100, sp.getCurrentMana());
		sp.setMaxMana(20);
		Assert.assertEquals(20, sp.getCurrentMana());
	}
	
	@Test
	public void test_IllegalMana() {
		StatusPoints sp = new StatusPoints();
		sp.setCurrentMana(-10);
		Assert.assertEquals(0, sp.getCurrentMana());
	}
	
	@Test
	public void illegal_low_hp() {
		StatusPoints sp = new StatusPoints();
		sp.setCurrentHp(-10);
		Assert.assertEquals(0, sp.getCurrentHp());
	}
	
	@Test
	public void illegal_low_maxHp() {
		StatusPoints sp = new StatusPoints();
		sp.setMaxHp(-10);
		Assert.assertEquals(1, sp.getMaxHp());
	}
	
	@Test
	public void illegal_low_maxMana() {
		StatusPoints sp = new StatusPoints();
		sp.setMaxMana(-10);
		Assert.assertEquals(1, sp.getMaxMana());
	}
	
	@Test
	public void unchanged_on_create() {
		StatusPoints sp = new StatusPoints();
		Assert.assertFalse(sp.hasChanged());
	}
	
	@Test
	public void changed_on_armordef() {
		StatusPoints sp = new StatusPoints();
		sp.setArmorDef(10);
		Assert.assertTrue(sp.hasChanged());
	}
	
	@Test
	public void changed_on_armorspdef() {
		StatusPoints sp = new StatusPoints();
		sp.setArmorSpDef(10);
		Assert.assertTrue(sp.hasChanged());
	}
	
	@Test
	public void changed_on_atk() {
		StatusPoints sp = new StatusPoints();
		sp.setAtk(10);
		Assert.assertTrue(sp.hasChanged());
	}
	
	@Test
	public void changed_on_hp() {
		StatusPoints sp = new StatusPoints();
		sp.setMaxHp(10);
		sp.setCurrentHp(10);
		Assert.assertTrue(sp.hasChanged());
	}
	
	@Test
	public void changed_on_mana() {
		StatusPoints sp = new StatusPoints();
		sp.setMaxMana(10);
		sp.setCurrentMana(10);
		Assert.assertTrue(sp.hasChanged());
	}
	
	@Test
	public void changed_on_def() {
		StatusPoints sp = new StatusPoints();
		sp.setDef(10);
		Assert.assertTrue(sp.hasChanged());
	}
	
	@Test
	public void changed_on_spatk() {
		StatusPoints sp = new StatusPoints();
		sp.setSpAtk(10);
		Assert.assertTrue(sp.hasChanged());
	}
	
	@Test
	public void changed_on_spd() {
		StatusPoints sp = new StatusPoints();
		sp.setSpd(10);
		Assert.assertTrue(sp.hasChanged());
	}
	
	@Test
	public void changed_on_spdef() {
		StatusPoints sp = new StatusPoints();
		sp.setSpDef(10);
		Assert.assertTrue(sp.hasChanged());
	}
	
	@Test
	public void changed_reset() {
		StatusPoints sp = new StatusPoints();
		sp.setSpDef(10);
		Assert.assertTrue(sp.hasChanged());
		sp.resetChanged();
		Assert.assertFalse(sp.hasChanged());
	}

}
