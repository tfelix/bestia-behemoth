package net.bestia.entity.factory;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.entity.Entity;
import net.bestia.entity.StatusService;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.ComponentSetter;
import net.bestia.entity.component.EquipComponent;
import net.bestia.entity.component.InventoryComponent;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.LevelComponentSetter;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.entity.component.PlayerComponentSetter;
import net.bestia.entity.component.PlayerStatusComponentSetter;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.PositionComponentSetter;
import net.bestia.entity.component.StatusComponent;
import net.bestia.entity.component.VisibleComponent;
import net.bestia.entity.component.VisibleComponentSetter;
import net.bestia.entity.factory.Blueprint;
import net.bestia.entity.factory.EntityFactory;
import net.bestia.entity.factory.PlayerBestiaEntityFactory;
import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.geometry.Point;

@RunWith(MockitoJUnitRunner.class)
public class PlayerBestiaEntityFactoryTest {

	private final static int LEVEL = 10;
	private final static int EXP = 124;
	private final static long ENTITY_ID = 666;
	// private final static long BASE_COMP_ID = 10;

	private PlayerBestiaEntityFactory factory;

	@Mock
	private EntityFactory entityFactory;

	@Mock
	private Bestia bestia;

	@Mock
	private PlayerBestia playerBestia;

	private Point currentPos = new Point(12, 56);

	@Mock
	private SpriteInfo spriteInfo;

	@Mock
	private StatusService statusService;

	@Captor
	private ArgumentCaptor<Set<ComponentSetter<? extends Component>>> componentSetterCaptor;

	@Captor
	private ArgumentCaptor<Blueprint> blueprintCaptor;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() {

		when(bestia.getSpriteInfo()).thenReturn(spriteInfo);

		when(playerBestia.getOrigin()).thenReturn(bestia);
		when(playerBestia.getLevel()).thenReturn(LEVEL);
		when(playerBestia.getExp()).thenReturn(EXP);
		when(playerBestia.getCurrentPosition()).thenReturn(currentPos);

		//when(entityFactory.buildEntity(any())).thenReturn(new Entity(ENTITY_ID));
		when(entityFactory.buildEntity(any(), any(Set.class))).thenReturn(new Entity(ENTITY_ID));

		factory = new PlayerBestiaEntityFactory(entityFactory, statusService);
	}

	@Test(expected = NullPointerException.class)
	public void bctor_arg1Null_throws() {
		new PlayerBestiaEntityFactory(null, statusService);
	}

	@Test(expected = NullPointerException.class)
	public void bctor_arg2Null_throws() {
		new PlayerBestiaEntityFactory(entityFactory, null);
	}

	@Test(expected = NullPointerException.class)
	public void build_null_throws() {
		factory.build(null);
	}

	@Test
	public void build_validPlayerBestia_builds() {
		Entity e = factory.build(playerBestia);

		Assert.assertNotNull(e);

		// Interceptor nutzen
		verify(entityFactory).buildEntity(blueprintCaptor.capture(), componentSetterCaptor.capture());

		// Assert the captor.
		Assert.assertTrue(blueprintCaptor.getValue().getComponents().contains(VisibleComponent.class));
		Assert.assertTrue(blueprintCaptor.getValue().getComponents().contains(EquipComponent.class));
		Assert.assertTrue(blueprintCaptor.getValue().getComponents().contains(InventoryComponent.class));
		Assert.assertTrue(blueprintCaptor.getValue().getComponents().contains(PositionComponent.class));
		Assert.assertTrue(blueprintCaptor.getValue().getComponents().contains(PlayerComponent.class));
		Assert.assertTrue(blueprintCaptor.getValue().getComponents().contains(LevelComponent.class));
		Assert.assertTrue(blueprintCaptor.getValue().getComponents().contains(StatusComponent.class));

		@SuppressWarnings("rawtypes")
		final Set<Class<? extends ComponentSetter>> compSetterClasses = componentSetterCaptor.getValue()
				.stream()
				.map(x -> x.getClass())
				.collect(Collectors.toSet());

		Assert.assertTrue(compSetterClasses.contains(PositionComponentSetter.class));
		Assert.assertTrue(compSetterClasses.contains(VisibleComponentSetter.class));
		Assert.assertTrue(compSetterClasses.contains(PlayerComponentSetter.class));
		Assert.assertTrue(compSetterClasses.contains(LevelComponentSetter.class));
		Assert.assertTrue(compSetterClasses.contains(PlayerStatusComponentSetter.class));

		verify(statusService).calculateStatusPoints(e);
	}

}
