package net.bestia.zoneserver.battle;

import org.junit.Test;
import org.mockito.Mock;

import net.bestia.model.domain.AttackImpl;

public class DamageCalculatorTest {
	
	@Mock
	private AttackImpl atk;

	@Test(expected=NullPointerException.class)
	public void calculate_1ArgNull_throws() {
		//DamageCalculator.calculate(null, user, target);
	}
	
	@Test(expected=NullPointerException.class)
	public void calculate_2ArgNull_throws() {
		//DamageCalculator.calculate(atk, null, target);
	}

	@Test(expected=NullPointerException.class)
	public void calculate_3ArgNull_throws() {
		//DamageCalculator.calculate(atk, user, null);
	}
	
	public void calculate_argsOkay_damage() {
		
	}

}
