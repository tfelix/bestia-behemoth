package net.bestia.zoneserver.entity.component;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.*;

import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;

@RunWith(MockitoJUnitRunner.class)
public class PlayerStatusComponentSetterTest {
	
	private PlayerStatusComponentSetter setter;
	
	private final static int CURRENT_HP = 123;
	private final static int CURRENT_MANA = 124;
	
	@Mock
	private PlayerBestia playerBestia;
	
	@Mock
	private Bestia bestia;
	
	@Mock
	private StatusComponent statusComp;
	
	@Mock
	private StatusPoints statusPoints;
	
	@Mock
	private StatusPoints origStatusPoints;
	
	@Before
	public void setup() {
		
		setter = new PlayerStatusComponentSetter(playerBestia);
		
		when(statusComp.getStatusPoints()).thenReturn(statusPoints);
		when(statusComp.getOriginalStatusPoints()).thenReturn(origStatusPoints);
		
		when(playerBestia.getCurrentHp()).thenReturn(CURRENT_HP);
		when(playerBestia.getCurrentMana()).thenReturn(CURRENT_MANA);
		when(playerBestia.getOrigin()).thenReturn(bestia);
		
		when(bestia.getElement()).thenReturn(Element.FIRE);
	}
	
	@Test
	public void getSupportedType_StatusCompClass() {
		Assert.assertEquals(setter.getSupportedType(), StatusComponent.class);
	}
	
	@Test(expected = NullPointerException.class)
	public void setComponent_null_throws() {
		setter.setComponent(null);
	}
	
	@Test
	public void setComponent_statusComponent_SetsCurrentHpAndMana() {
		
		setter.setComponent(statusComp);
		
		verify(origStatusPoints).setCurrentHp(CURRENT_HP);
		verify(origStatusPoints).setCurrentMana(CURRENT_MANA);
		verify(origStatusPoints).setMaxHp(CURRENT_HP);
		verify(origStatusPoints).setMaxMana(CURRENT_MANA);
		
		verify(statusComp).setOriginalElement(Element.FIRE);
	}

}
