package net.bestia.entity.component;

import net.bestia.model.domain.*;
import net.bestia.zoneserver.entity.component.StatusComponent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
		
		verify(statusComp).setConditionValues(statusValues);
		verify(statusComp).setUnmodifiedElement(Element.FIRE);
	}

}
