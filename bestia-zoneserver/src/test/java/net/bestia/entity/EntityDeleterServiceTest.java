package net.bestia.entity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.entity.component.Component;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.recycler.ComponentDeleter;
import net.bestia.entity.recycler.EntityCache;
import net.bestia.messages.internal.entity.EntityDeleteInternalMessage;
import net.bestia.zoneserver.actor.ZoneAkkaApi;

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
		deleter.deleteEntity(entity);
		
		verify(entityService).deleteComponent(p1);
		verify(entityService).delete(entity);
		verify(akkaApi).sendEntityActor(VALID_ENTITY_ID, any(EntityDeleteInternalMessage.class));
		verify(cache).stashComponente(p1);
		verify(cache).stashEntity(entity);
	}

	@Test
	public void deleteComponent_validEntityAndComponent_componentRemoved() {
		deleter.deleteComponent(entity, PositionComponent.class);
		
		verify(entityService, times(0)).delete(entity);
		verify(entityService).deleteComponent(p1);
		verify(cache).stashComponente(p1);
	}
}
