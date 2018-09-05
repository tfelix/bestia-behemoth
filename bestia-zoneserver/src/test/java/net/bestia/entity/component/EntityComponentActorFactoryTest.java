package net.bestia.entity.component;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import net.bestia.entity.EntityService;
import net.bestia.zoneserver.actor.entity.component.EntityComponentActorFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

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
	private ActorContext ctx;

	@Before
	public void setup() {

		when(entityService.getComponent(EXISTING_COMP_ID)).thenReturn(posComp);
		when(entityService.getComponent(NON_CREAT_MODULE_COMP_ID)).thenReturn(statComp);
		when(entityService.getComponent(NOT_EXISTING_COMP_ID)).thenReturn(null);
		
		fac = new EntityComponentActorFactory(entityService);
	}

	@Test
	public void startActor_notExistingComponentId_null() {
		ActorRef actor = fac.startActor(ctx, NOT_EXISTING_COMP_ID);
		Assert.assertNull(actor);
	}
	
	@Test
	public void startActor_notExistingModule_null() {
		ActorRef actor = fac.startActor(ctx, NON_CREAT_MODULE_COMP_ID);
		Assert.assertNull(actor);
	}

	@Test
	public void startActor_existingComponentId_startsActor() {
		ActorRef actor = fac.startActor(ctx, EXISTING_COMP_ID);
		Assert.assertNotNull(actor);
	}
}
