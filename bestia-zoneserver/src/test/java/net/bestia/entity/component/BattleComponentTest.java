package net.bestia.entity.component;

import java.util.Map;

import net.entity.component.BattleComponent;
import org.junit.Assert;
import org.junit.Test;

public class BattleComponentTest {

	
	@Test
	public void addDamageReceived_negativeId_doesNothing() {
		BattleComponent bc = new BattleComponent(1);
		bc.addDamageReceived(-1, 10);
		Assert.assertEquals(0, bc.getDamageDealers().size());
	}
	
	@Test
	public void addDamageReceived_negativeOrNullDamage_doesNothing() {
		BattleComponent bc = new BattleComponent(1);
		bc.addDamageReceived(1, 0);
		bc.addDamageReceived(2, -10);
		
		Assert.assertEquals(0, bc.getDamageDealers().size());
	}
	
	@Test
	public void addDamageReceived_validNumberOfDamage_isAdded() {
		BattleComponent bc = new BattleComponent(1);
		bc.addDamageReceived(1, 10);
		Assert.assertEquals(1, bc.getDamageDealers().size());
	}
	
	@Test
	public void addDamageReceived_notMoreThenCertainMax() {
		BattleComponent bc = new BattleComponent(1);
		
		final int maxTest = 1000;
		
		for(int i = 0; i < maxTest; i++) {
			bc.addDamageReceived(i, 10);
		}
		
		Assert.assertTrue(bc.getDamageDealers().size() < maxTest);
	}
	
	@Test
	public void clearDamageEntries_clearsTheEntries() {
		BattleComponent bc = new BattleComponent(1);
		bc.addDamageReceived(1, 10);
		bc.clearDamageEntries();
		Assert.assertEquals(0, bc.getDamageDealers().size());
	}
	
	@Test
	public void getDamageDistribution_containsAllDamageDealersPercentage() {
		BattleComponent bc = new BattleComponent(1);
		bc.addDamageReceived(1, 10);
		bc.addDamageReceived(2, 20);
		bc.addDamageReceived(2, 20);
		bc.addDamageReceived(4, 50);
		
		Map<Long, Double> dist = bc.getDamageDistribution();
		
		Assert.assertEquals(0.1, dist.get(1L), 0.001);
		Assert.assertEquals(0.4, dist.get(2L), 0.001);
		Assert.assertEquals(0.5, dist.get(4L), 0.001);
	}
}
