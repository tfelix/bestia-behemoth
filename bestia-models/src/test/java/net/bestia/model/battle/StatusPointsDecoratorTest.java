package net.bestia.model.battle;

import static org.mockito.Mockito.*;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.model.domain.StatusPoints;

@RunWith(MockitoJUnitRunner.class)
public class StatusPointsDecoratorTest {

	private StatusPointsDecorator spDeco;

	@Mock
	private StatusPointsModifier mod;

	@Mock
	private StatusPoints sp;

	@Before
	public void setup() {
		spDeco = new StatusPointsDecorator(sp);
		
		when(sp.getDexterity()).thenReturn(10);
		
		when(mod.getDexterityValue()).thenReturn(10);
		when(mod.getDexterityMod()).thenReturn(1.2f);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullWrapper_throws() {
		new StatusPointsDecorator(null);
	}

	@Test
	public void addModifier_null_nothing() {
		spDeco.addModifier(null);
	}

	@Test
	public void addModifier_validMod_addsUpValue() {
		spDeco.addModifier(mod);
		final int newDex = spDeco.getDexterity();
		Assert.assertEquals(10 * 1.2 + 10, newDex, 0.01);
	}

	@Test
	public void removeModifier_null_nothing() {
		spDeco.addModifier(mod);
		spDeco.removeModifier(null);
		Assert.assertTrue(spDeco.getDexterity() > 10);
	}

	@Test
	public void removeModifier_previouslyAdded_removed() {
		spDeco.addModifier(mod);
		Assert.assertTrue(spDeco.getDexterity() > 10);
		spDeco.removeModifier(mod);
		Assert.assertTrue(spDeco.getDexterity() == 10);
	}

	@Test
	public void removeModifier_notAdded_nothing() {

		spDeco.addModifier(mod);
		Assert.assertTrue(spDeco.getDexterity() > 10);
		
		StatusPointsModifier mod2 = mock(StatusPointsModifier.class);
		spDeco.removeModifier(mod2);
		
		Assert.assertTrue(spDeco.getDexterity() > 10);
	}
}
