package net.bestia.entity;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import akka.util.Collections;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.recycler.ComponentDeleter;
import net.bestia.entity.recycler.EntityCache;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.script.ScriptService;

@RunWith(MockitoJUnitRunner.class)
public class EntityDeleterServiceTest {

	private static final long INVALID_ENTITY_ID = 123;
	private static final long VALID_ENTITY_ID = 13;

	private EntityDeleterService deleter;

	@Mock
	private EntityService entityService;

	@Mock
	private ZoneAkkaApi akkaApi;

	@Mock
	private EntityCache cache;

	private List<ComponentDeleter<? extends Component>> deleters = new ArrayList<>();

	@Mock
	private PositionComponent p1;

	@Mock
	private PositionComponent p2;

	@Mock
	private Entity entity;

	@Mock
	private Entity entity2;

	@Before
	public void setup() {

		// when(entityService.hasComponent(entity,
		// PositionComponent.class)).thenReturn(true);
		// when(entityService.getComponent(entity,
		// PositionComponent.class)).thenReturn(Optional.of(p1));

		// when(entityService.hasComponent(entity2,
		// PositionComponent.class)).thenReturn(true);
		// when(entityService.getComponent(entity2,
		// PositionComponent.class)).thenReturn(Optional.of(p2));

		when(entityService.getEntity(INVALID_ENTITY_ID)).thenReturn(null);
		when(entityService.getEntity(VALID_ENTITY_ID)).thenReturn(entity);
		when(entityService.getAllComponents(entity)).thenReturn(Stream.of(p1).collect(Collectors.toList()));
		when(entityService.getAllComponents(entity2)).thenReturn(Stream.of(p2).collect(Collectors.toList()));

		deleter = new EntityDeleterService(cache, entityService, akkaApi, deleters);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullCache_throws() {
		new EntityDeleterService(null, entityService, akkaApi, deleters);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullService_throws() {
		new EntityDeleterService(cache, null, akkaApi, deleters);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullAkkaApi_throws() {
		new EntityDeleterService(cache, entityService, null, deleters);
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullDeleters_throws() {
		new EntityDeleterService(cache, entityService, akkaApi, null);
	}
	
/*
	@Test
	public void free_validEntity_cachesAllComponentsEntityIsReturnedAgain() {

		deleter.deleteEntity(entity);
		deleter.deleteEntity(entity2);

		verify(entityService).deleteAllComponents(entity);
		verify(entityService).deleteAllComponents(entity2);
		verify(entityService).delete(entity);
		verify(entityService).delete(entity2);

		Entity cachedEntity = recycler.getEntity();
		Assert.assertEquals(entity, cachedEntity);

		// Only once item cached.
		cachedEntity = recycler.getEntity();
		Assert.assertNull(cachedEntity);

		PositionComponent cachedComp = recycler.getComponent(p1.getClass());
		Assert.assertNotNull(cachedComp);

		cachedComp = recycler.getComponent(p2.getClass());
		Assert.assertNull(cachedComp);
	}*/

	@Test(expected = NullPointerException.class)
	public void deleteEntity_null_throws() {
		deleter.deleteEntity(null);
	}

	@Test(expected = NullPointerException.class)
	public void deleteComponent_nullEntity_throws() {
		deleter.deleteComponent(null, PositionComponent.class);
	}

	@Test(expected = NullPointerException.class)
	public void deleteComponent_nullComponent_throws() {
		deleter.deleteComponent(entity, null);
	}

	@Test
	public void deleteEntity_entity_entityIsCompletlyRemoved() {

	}

	@Test
	public void deleteComponent_validEntityAndComponent_componentRemoved() {

	}
}
