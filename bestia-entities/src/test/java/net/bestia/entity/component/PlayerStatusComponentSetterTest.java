package net.bestia.entity.component;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.*;

import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.ConditionValues;

@RunWith(MockitoJUnitRunner.class)
public class PlayerStatusComponentSetterTest {
	
	private PlayerStatusComponentSetter setter;
	
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
	
	@Mock
	private ConditionValues statusValues;
	
	@Before
	public void setup() {
		
		setter = new PlayerStatusComponentSetter(playerBestia);
		
		when(playerBestia.getOrigin()).thenReturn(bestia);
		when(playerBestia.getStatusValues()).thenReturn(statusValues);
		
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
		
		verify(statusComp).setStatusValues(statusValues);
		verify(statusComp).setUnmodifiedElement(Element.FIRE);
	}

}
