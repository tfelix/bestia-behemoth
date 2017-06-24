package net.bestia.entity;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

import java.util.Collection;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.testing.BasicMocks;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.VisibleComponent;
import net.bestia.entity.component.interceptor.ComponentInterceptor;

public class EntityServiceTest {

	private EntityService entityService;

	private BasicMocks basicMocks = new BasicMocks();
	private HazelcastInstance hz = basicMocks.hazelcastMock();
	private ComponentInterceptor<PositionComponent> interceptor;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() {
		
		interceptor = (ComponentInterceptor<PositionComponent>) mock(ComponentInterceptor.class);
		
		when(interceptor.getTriggerType()).thenReturn(PositionComponent.class);
		
		entityService = new EntityService(hz);
	}

	@Test(expected = NullPointerException.class)
	public void addInterceptor_null_throws() {
		entityService.addInterceptor(null);
	}

	@Test
	public void addInterceptor_intercetor_calledWhenComponentUpdateCreateDelete() {
		entityService.addInterceptor(interceptor);
		
		verify(interceptor).getTriggerType();
		
		Entity e1 = entityService.newEntity();
		PositionComponent comp = entityService.addComponent(e1, PositionComponent.class);
		
		verify(interceptor).triggerCreateAction(entityService, e1, comp);
		
		comp.setShape(new Rect(10, 10));
		entityService.saveComponent(comp);
		
		verify(interceptor).triggerUpdateAction(entityService, e1, comp);
		
		entityService.deleteComponent(e1, comp);
		
		verify(interceptor).triggerDeleteAction(entityService, e1, comp);
		
		comp = entityService.addComponent(e1, PositionComponent.class);
		entityService.deleteAllComponents(e1);
		verify(interceptor).triggerDeleteAction(entityService, e1, comp);
	}

	@Test
	public void newEntity_returnsNewEntity() {
		Entity e1 = entityService.newEntity();
		Entity e2 = entityService.newEntity();
		Assert.assertNotEquals(0, e1.getId());
		Assert.assertNotEquals(0, e2.getId());
		Assert.assertNotEquals(e1, e2);
		Assert.assertNotNull(e1);
	}

	@Test(expected = NullPointerException.class)
	public void addComponent_nullComp_throws() {
		Entity e1 = entityService.newEntity();
		entityService.addComponent(e1, null);
	}

	@Test
	public void addComponent_entityRefAndComponent_isAdded() {
		Entity e1 = entityService.newEntity();
		PositionComponent posComp = entityService.addComponent(e1, PositionComponent.class);
		Assert.assertNotNull(posComp);
		Assert.assertTrue(entityService.hasComponent(e1, PositionComponent.class));
	}

	@Test(expected = NullPointerException.class)
	public void delete_null_throws() {
		entityService.delete(null);
	}

	@Test
	public void delete_notSavedEntity_doesNothing() {
		Entity e = entityService.newEntity();
		entityService.delete(e);
	}

	@Test
	public void delete_savedEntity_deletesIt() {
		Entity e = entityService.newEntity();
		entityService.delete(e);
		Entity e2 = entityService.getEntity(e.getId());
		Assert.assertNull(e2);
	}

	@Test
	public void save_entity_savedIt() {
		Entity e = entityService.newEntity();
		Entity e2 = entityService.getEntity(e.getId());
		Assert.assertEquals(e, e2);
	}

	@Test
	public void addComponent_entityIdAndComponent_isAdded() {
		Entity e1 = entityService.newEntity();
		PositionComponent posComp = entityService.addComponent(e1, PositionComponent.class);
		boolean hasComp = entityService.hasComponent(e1, PositionComponent.class);

		Assert.assertNotNull(posComp);
		Assert.assertTrue("Entity does not have component.", hasComp);

		posComp.setShape(new Point(10, 10));
		posComp.setPosition(123, 123);
		entityService.saveComponent(posComp);

		PositionComponent posComp2 = entityService.getComponent(e1, PositionComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		Assert.assertEquals(posComp, posComp2);
	}

	@Test
	public void hasComponent_notAddedComponent_false() {
		Entity e1 = entityService.newEntity();
		Assert.assertFalse(entityService.hasComponent(e1, VisibleComponent.class));
	}

	@Test
	public void hasComponent_addedComponent_true() {
		Entity e1 = entityService.newEntity();
		entityService.addComponent(e1, PositionComponent.class);
		Assert.assertTrue(entityService.hasComponent(e1, PositionComponent.class));
	}
	
	@Test(expected=NullPointerException.class)
	public void hasComponent_nullEntity_throws() {
		entityService.hasComponent(null, PositionComponent.class);
	}
	
	@Test(expected=NullPointerException.class)
	public void hasComponent_nullClass_throws() {
		Entity e1 = entityService.newEntity();
		entityService.hasComponent(e1, null);
	}
	
	@Test(expected=NullPointerException.class)
	public void getAllComponents_null_throws() {
		entityService.getAllComponents(null);
	}
	
	
	@Test
	public void getAllComponents_validEntity_returnsAllComponents() {
		Entity e1 = entityService.newEntity();
		PositionComponent pc = entityService.addComponent(e1, PositionComponent.class);
		VisibleComponent vc = entityService.addComponent(e1, VisibleComponent.class);
		
		Collection<Component> comps = entityService.getAllComponents(e1);
		Assert.assertEquals(2, comps.size());
		Assert.assertTrue(comps.contains(pc));
		Assert.assertTrue(comps.contains(vc));
	}
	
	@Test(expected=NullPointerException.class)
	public void deleteComponent_nullEntity_throws() {
		Entity e1 = entityService.newEntity();
		PositionComponent pc = entityService.addComponent(e1, PositionComponent.class);
		entityService.deleteComponent(null, pc);
	}
	
	@Test(expected=NullPointerException.class)
	public void deleteComponent_nullComponent_throws() {
		Entity e1 = entityService.newEntity();
		entityService.deleteComponent(e1, null);
	}
	
	@Test(expected=NullPointerException.class)
	public void deleteAllComponents_nullEntity_throws() {
		entityService.deleteAllComponents(null);
	}
	
	@Test
	public void deleteAllComponents_validEntity_deletesAllComponentsCallsInterceptor() {
		Entity e1 = entityService.newEntity();
		PositionComponent pc = entityService.addComponent(e1, PositionComponent.class);
		entityService.addInterceptor(interceptor);
		
		entityService.deleteAllComponents(e1);
		
		Collection<Component> comps = entityService.getAllComponents(e1);
		Assert.assertEquals(0, comps.size());
		
		verify(interceptor).triggerDeleteAction(entityService, e1, pc);
	}
}
