package net.bestia.zoneserver.entity;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.bestia.zoneserver.entity.components.Component;
import net.bestia.zoneserver.entity.components.ComponentSetter;
import net.bestia.zoneserver.entity.components.PlayerComponent;
import net.bestia.zoneserver.entity.components.PlayerComponentSetter;

public class EntityFactoryTest {

	private EntityFactory factory;
	private EntityService entityService;
	private Blueprint blueprint;
	private Entity entity;

	private PlayerComponentSetter playerSet;
	private PlayerComponent playerComp;
	private Collection<Class<? extends Component>> components = new HashSet<>();

	@Before
	public void setup() {

		components.clear();
		components.add(PlayerComponent.class);

		entityService = mock(EntityService.class);
		blueprint = mock(Blueprint.class);
		entity = mock(Entity.class);
		playerComp = mock(PlayerComponent.class);

		playerSet = mock(PlayerComponentSetter.class);

		when(entityService.newEntity()).thenReturn(entity);
		when(entityService.addComponent(entity, PlayerComponent.class)).thenReturn(playerComp);

		when(blueprint.getComponents()).thenReturn(components);
		
		when(playerSet.getSupportedType()).thenReturn(PlayerComponent.class);

		factory = new EntityFactory(entityService);
	}

	@Test(expected = NullPointerException.class)
	public void build_nullBlueprint_throws() {
		factory.build(null);
	}

	@Test(expected = NullPointerException.class)
	public void build_nullComponentSetter_throws() {
		factory.build(blueprint, null);
	}

	@Test
	public void makeSet() {
		Set<ComponentSetter<? extends Component>> set = EntityFactory.makeSet(playerSet);
		Assert.assertTrue(set.contains(playerSet));
	}

	@Test
	public void build_validSetter_works() {
		Set<ComponentSetter<? extends Component>> set = EntityFactory.makeSet(playerSet);
		Entity e = factory.build(blueprint, set);

		Assert.assertNotNull(e);

		verify(entityService).newEntity();
		verify(entityService).addComponent(e, PlayerComponent.class);
		verify(entityService).updateComponent(any());
	}

}
