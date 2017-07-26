package net.bestia.entity;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collection;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.testing.BasicMocks;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.VisibleComponent;
import net.bestia.entity.component.interceptor.ComponentInterceptor;

@RunWith(MockitoJUnitRunner.class)
public class EntityServiceTest {

	private EntityService entityService;

	private BasicMocks basicMocks = new BasicMocks();

	@Mock
	private PositionComponent posComp;

	@Mock
	private PositionComponent ownedComp;

	private HazelcastInstance hz = basicMocks.hazelcastMock();

	private ComponentInterceptor<PositionComponent> interceptor;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() {

		interceptor = (ComponentInterceptor<PositionComponent>) mock(ComponentInterceptor.class);

		when(interceptor.getTriggerType()).thenReturn(PositionComponent.class);
		when(ownedComp.getEntityId()).thenReturn(1337L);

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
		PositionComponent comp = entityService.newComponent(PositionComponent.class);
		comp.setShape(new Rect(10, 10));
		// Should no called until all values are set.
		verify(interceptor, times(0)).triggerCreateAction(entityService, e1, comp);

		entityService.attachComponent(e1, comp);

		verify(interceptor, times(1)).triggerCreateAction(entityService, e1, comp);

		entityService.updateComponent(comp);

		verify(interceptor).triggerUpdateAction(entityService, e1, comp);

		entityService.deleteComponent(e1, comp);
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
		entityService.newComponent(null);
	}

	@Test
	public void addComponent_entityRefAndComponent_isAdded() {
		Entity e1 = entityService.newEntity();
		PositionComponent posComp = entityService.newComponent(PositionComponent.class);

		Assert.assertNotNull(posComp);
		Assert.assertFalse(entityService.hasComponent(e1, PositionComponent.class));
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
		PositionComponent posComp = entityService.newComponent(PositionComponent.class);
		boolean hasComp = entityService.hasComponent(e1, PositionComponent.class);

		Assert.assertNotNull(posComp);
		Assert.assertFalse("Entity does not have component.", hasComp);

		posComp.setShape(new Point(10, 10));
		posComp.setPosition(123, 123);

		entityService.attachComponent(e1, posComp);
		entityService.updateComponent(posComp);

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
		PositionComponent pc = entityService.newComponent(PositionComponent.class);
		entityService.attachComponent(e1, pc);
		Assert.assertTrue(entityService.hasComponent(e1, PositionComponent.class));
	}

	@Test(expected = NullPointerException.class)
	public void hasComponent_nullEntity_throws() {
		entityService.hasComponent(null, PositionComponent.class);
	}

	@Test(expected = NullPointerException.class)
	public void hasComponent_nullClass_throws() {
		Entity e1 = entityService.newEntity();
		entityService.hasComponent(e1, null);
	}

	@Test(expected = NullPointerException.class)
	public void getAllComponents_null_throws() {
		entityService.getAllComponents(null);
	}

	@Test(expected = NullPointerException.class)
	public void attachComponent_nullEntity_throws() {
		entityService.attachComponent(null, posComp);
	}

	@Test(expected = NullPointerException.class)
	public void attachComponent_nullComponent_throws() {
		Entity e = entityService.newEntity();
		entityService.attachComponent(e, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void attachComponent_ownedComponent_throws() {
		Entity e = entityService.newEntity();
		entityService.attachComponent(e, ownedComp);
	}

	public void attachComponent_component_attached() {
		Entity e = entityService.newEntity();
		entityService.attachComponent(e, posComp);

		verify(posComp).setEntityId(e.getId());
	}

	@Test
	public void getAllComponents_validEntity_returnsAllComponents() {
		Entity e1 = entityService.newEntity();

		PositionComponent pc = entityService.newComponent(PositionComponent.class);
		VisibleComponent vc = entityService.newComponent(VisibleComponent.class);

		entityService.attachComponents(e1, Arrays.asList(pc, vc));

		Collection<Component> comps = entityService.getAllComponents(e1);
		Assert.assertEquals(2, comps.size());
		Assert.assertTrue(comps.contains(pc));
		Assert.assertTrue(comps.contains(vc));
	}

	@Test(expected = NullPointerException.class)
	public void deleteComponent_nullComponent_throws() {
		Entity e1 = entityService.newEntity();
		entityService.deleteComponent(e1, null);
	}

	@Test(expected = NullPointerException.class)
	public void deleteAllComponents_nullEntity_throws() {
		entityService.deleteAllComponents(null);
	}

	@Test(expected = NullPointerException.class)
	public void deleteComponent_validEntity_dontHaveComponentAnymore() {
		Entity e = entityService.newEntity();
		PositionComponent posComp = new PositionComponent(10);
		entityService.attachComponent(e, posComp);

		Assert.assertEquals(1, e.getComponentIds().size());
		Assert.assertTrue(e.getComponentId(PositionComponent.class) == 10);

		entityService.deleteComponent(e, posComp);
		
		Assert.assertEquals(0, e.getComponentIds().size());
		Assert.assertTrue(e.getComponentId(PositionComponent.class) == 0);
	}

	@Test
	public void deleteAllComponents_validEntity_deletesAllComponentsCallsInterceptor() {
		Entity e1 = entityService.newEntity();

		entityService.addInterceptor(interceptor);

		PositionComponent pc = entityService.newComponent(PositionComponent.class);
		VisibleComponent vc = entityService.newComponent(VisibleComponent.class);
		entityService.attachComponents(e1, Arrays.asList(pc, vc));

		Assert.assertEquals(2, e1.getComponentIds().size());
		Assert.assertEquals(2, entityService.getAllComponents(e1).size());

		entityService.deleteAllComponents(e1);

		Assert.assertEquals(0, entityService.getAllComponents(e1).size());
		Assert.assertEquals(0, e1.getComponentIds().size());
	}
}
