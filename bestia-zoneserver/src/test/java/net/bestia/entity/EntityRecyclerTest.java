package net.bestia.entity;

import static org.mockito.Mockito.*;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.recycler.EntityRecycler;
import net.bestia.zoneserver.script.ScriptService;

@RunWith(MockitoJUnitRunner.class)
public class EntityRecyclerTest {
	
	private static final long INVALID_ENTITY_ID = 123;
	private static final long VALID_ENTITY_ID = 13;
	

	private EntityRecycler recycler;
	
	@Mock
	private EntityService entityService;
	
	@Mock
	private ScriptService scriptService;
	
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
		
		//when(entityService.hasComponent(entity, PositionComponent.class)).thenReturn(true);
		//when(entityService.getComponent(entity, PositionComponent.class)).thenReturn(Optional.of(p1));
		
		//when(entityService.hasComponent(entity2, PositionComponent.class)).thenReturn(true);
		//when(entityService.getComponent(entity2, PositionComponent.class)).thenReturn(Optional.of(p2));
		
		when(entityService.getEntity(INVALID_ENTITY_ID)).thenReturn(null);
		when(entityService.getEntity(VALID_ENTITY_ID)).thenReturn(entity);
		when(entityService.getAllComponents(entity)).thenReturn(Stream.of(p1).collect(Collectors.toList()));
		when(entityService.getAllComponents(entity2)).thenReturn(Stream.of(p2).collect(Collectors.toList()));
		
		recycler = new EntityRecycler(1, entityService, scriptService);
	}

	@Test(expected=IllegalArgumentException.class)
	public void ctor_negativeCacheValues_throws() {
		new EntityRecycler(-10, entityService, scriptService);
	}

	@Test(expected=NullPointerException.class)
	public void ctor_nullEntityServiceCtx_throws() {
		new EntityRecycler(10, null, scriptService);
	}

	@Test(expected=NullPointerException.class)
	public void free_null_throws() {
		recycler.free(null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void free_invalidEntityId_throws() {
		recycler.free(INVALID_ENTITY_ID);
	}

	@Test
	public void free_validEntity_cachesAllComponentsEntityIsReturnedAgain() {
		
		recycler.free(entity);
		recycler.free(entity2);
		
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
	}
	
	@Test
	public void free_validEntityId_cachesAllComponentsEntityIsReturnedAgain() {
		recycler.free(VALID_ENTITY_ID);
		
		verify(entityService).deleteAllComponents(entity);
		verify(entityService).delete(entity);
		
		Entity cachedEntity = recycler.getEntity();
		Assert.assertEquals(entity, cachedEntity);
		
		PositionComponent cachedComp = recycler.getComponent(p1.getClass());
		Assert.assertEquals(p1, cachedComp);
	}
	
	@Test
	public void getEntity_noEntityCached_null() {
		Assert.assertNull(recycler.getEntity());
	}
	
	@Test
	public void getComponent_noComponentCached_null() {
		Assert.assertNull(recycler.getComponent(PositionComponent.class));
	}
	
	// Cannot be easily tested because of mockito name.
	//@Test
	public void getComponent_componentCached_component() {
		recycler.free(entity);
		PositionComponent posComp = recycler.getComponent(PositionComponent.class);
		Assert.assertNotNull(posComp);
	}
}
