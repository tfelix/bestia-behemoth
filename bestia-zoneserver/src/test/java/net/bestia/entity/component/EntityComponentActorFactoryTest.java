package net.bestia.entity.component;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.*;

import java.util.Arrays;

import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorRef;
import net.bestia.entity.EntityService;

@RunWith(MockitoJUnitRunner.class)
public class EntityComponentActorFactoryTest {

	private static final long NOT_EXISTING_COMP_ID = 1;
	private static final long EXISTING_COMP_ID = 2;
	private static final long NON_CREAT_MODULE_COMP_ID = 3;
	
	private EntityComponentActorFactory fac;
	
	@Mock
	private EntityService entityService;
	
	@Mock
	private PositionComponent posComp;
	
	@Mock
	private StatusComponent statComp;
	
	@Mock
	private ActorRef ref;
	
	@Mock
	ActorComponentFactoryModule<PositionComponent> posActorModule;

	@Before
	public void setup() {

		when(entityService.getComponent(EXISTING_COMP_ID)).thenReturn(posComp);
		when(entityService.getComponent(NON_CREAT_MODULE_COMP_ID)).thenReturn(statComp);
		when(entityService.getComponent(NOT_EXISTING_COMP_ID)).thenReturn(null);
		
		when(posActorModule.buildActor(posComp)).thenReturn(ref);
		
		fac = new EntityComponentActorFactory(entityService, Arrays.asList(posActorModule));
	}

	@Test
	public void startActor_notExistingComponentId_null() {
		ActorRef actor = fac.startActor(NOT_EXISTING_COMP_ID);
		Assert.assertNull(actor);
	}
	
	@Test
	public void startActor_notExistingModule_null() {
		ActorRef actor = fac.startActor(NON_CREAT_MODULE_COMP_ID);
		Assert.assertNull(actor);
	}

	@Test
	public void startActor_existingComponentId_startsActor() {
		ActorRef actor = fac.startActor(EXISTING_COMP_ID);
		
		verify(posActorModule).buildActor(posComp);
		Assert.assertNotNull(actor);
	}
}
