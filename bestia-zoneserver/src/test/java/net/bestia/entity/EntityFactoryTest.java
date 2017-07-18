package net.bestia.entity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.entity.component.Component;
import net.bestia.entity.component.ComponentSetter;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.entity.component.PlayerComponentSetter;
import net.bestia.entity.recycler.EntityCache;
import net.bestia.zoneserver.actor.ZoneAkkaApi;

@RunWith(MockitoJUnitRunner.class)
public class EntityFactoryTest {

	private EntityFactory factory;
	
	@Mock
	private EntityService entityService;
	
	@Mock
	private Blueprint blueprint;
	
	@Mock
	private Entity entity;

	@Mock
	private PlayerComponentSetter playerSet;
	
	@Mock
	private PlayerComponent playerComp;
	
	@Mock
	private ZoneAkkaApi akkaApi;
	
	@Mock
	private EntityCache cache;
	
	private Collection<Class<? extends Component>> components = new HashSet<>();

	@Before
	public void setup() {

		components.clear();
		components.add(PlayerComponent.class);

		when(entityService.newEntity()).thenReturn(entity);
		when(entityService.addComponent(entity, PlayerComponent.class)).thenReturn(playerComp);
		
		when(blueprint.getComponents()).thenReturn(components);
		
		when(playerSet.getSupportedType()).thenReturn(PlayerComponent.class);

		factory = new EntityFactory(entityService, akkaApi, cache);
	}

	@Test(expected = NullPointerException.class)
	public void build_nullBlueprint_throws() {
		factory.buildEntity(null);
	}

	@Test(expected = NullPointerException.class)
	public void build_nullComponentSetter_throws() {
		factory.buildEntity(blueprint, null);
	}

	@Test
	public void makeSet() {
		Set<ComponentSetter<? extends Component>> set = EntityFactory.makeSet(playerSet);
		Assert.assertTrue(set.contains(playerSet));
	}

	@Test
	public void build_validSetter_works() {
		Set<ComponentSetter<? extends Component>> set = EntityFactory.makeSet(playerSet);
		Entity e = factory.buildEntity(blueprint, set);

		Assert.assertNotNull(e);

		verify(entityService).newEntity();
		verify(entityService).addComponent(e, PlayerComponent.class);
		verify(entityService).updateComponent(any());
	}

}
