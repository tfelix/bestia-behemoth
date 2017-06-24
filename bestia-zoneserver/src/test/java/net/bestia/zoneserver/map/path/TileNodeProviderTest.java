package net.bestia.zoneserver.map.path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.PositionComponent;

@RunWith(MockitoJUnitRunner.class)
public class TileNodeProviderTest {

	@Mock
	private EntityService entityService;

	@Mock
	private Map gameMap;

	@Mock
	private PositionComponent posComp;

	private final static Point POINT_OUT_OF_RANGE = new Point(1000, 1000);
	private final static Node<Point> NODE_OUT_OF_RANGE = new Node<>(POINT_OUT_OF_RANGE);

	private final static Point POINT_IN_RANGE = new Point(10, 10);
	private final static Node<Point> NODE_IN_RANGE = new Node<>(POINT_IN_RANGE);

	private final static Point POINT_ENTITY_BLOCK = new Point(12, 12);
	private final static Node<Point> NODE_ENTITY_BLOCK = new Node<>(POINT_ENTITY_BLOCK);

	private final Rect collisionShape = new Rect(0, 0, 10000, 10000);
	private final static Entity blockingEntity;
	private final static Set<Entity> blockingEntities = new HashSet<>();

	static {
		blockingEntity = Mockito.mock(Entity.class);
		blockingEntities.add(blockingEntity);
	}

	private TileNodeProvider provider;

	@Before
	public void setup() {

		when(gameMap.isWalkable(POINT_OUT_OF_RANGE.getX(), POINT_OUT_OF_RANGE.getY())).thenReturn(false);
		when(gameMap.isWalkable(POINT_IN_RANGE.getX(), POINT_IN_RANGE.getY())).thenReturn(true);
		when(gameMap.isWalkable(POINT_ENTITY_BLOCK.getX(), POINT_ENTITY_BLOCK.getY())).thenReturn(true);
		when(gameMap.isWalkable(anyLong(), anyLong())).thenReturn(true);

		when(entityService.getCollidingEntities(POINT_ENTITY_BLOCK)).thenReturn(blockingEntities);
		when(entityService.getComponent(blockingEntity, PositionComponent.class)).thenReturn(Optional.of(posComp));
		when(posComp.getShape()).thenReturn(collisionShape);

		provider = new TileNodeProvider(gameMap, entityService);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullMap_throws() {
		new TileNodeProvider(null, entityService);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullEntityService_throws() {
		new TileNodeProvider(gameMap, null);
	}

	@Test(expected = NullPointerException.class)
	public void getConnectedNodes_nullNode_throws() {
		provider.getConnectedNodes(null);
	}

	@Test
	public void getConnectedNodes_nodeInMapRange_allConnections() {
		Set<Node<Point>> cons = provider.getConnectedNodes(NODE_IN_RANGE);
		assertFalse(cons.isEmpty());
	}

	@Test
	public void getConnectedNodes_nodeOutOfMapRange_emptyConnections() {
		Set<Node<Point>> cons = provider.getConnectedNodes(NODE_OUT_OF_RANGE);
		assertTrue(cons.isEmpty());
	}

	@Test
	public void getConnectedNodes_nodeBlockedByEntity_notGivenAsConnection() {
		Set<Node<Point>> cons = provider.getConnectedNodes(NODE_ENTITY_BLOCK);
		assertTrue(cons.isEmpty());
	}
}
