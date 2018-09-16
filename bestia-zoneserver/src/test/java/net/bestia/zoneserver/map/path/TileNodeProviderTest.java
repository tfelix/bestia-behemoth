package net.bestia.zoneserver.map.path;

import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.PositionComponent;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.model.map.Walkspeed;
import net.bestia.zoneserver.entity.EntitySearchService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TileNodeProviderTest {

  @Mock
  private EntityService entityService;

  private EntitySearchService entitySearchService;

  @Mock
  private Map gameMap;

  @Mock
  private PositionComponent posComp;

  private final static Point POINT_OUT_OF_RANGE = new Point(1000, 1000);
  private final static Node<Point> NODE_OUT_OF_RANGE = new Node<>(POINT_OUT_OF_RANGE);

  private final static Point POINT_IN_RANGE = new Point(10, 10);
  private final static Node<Point> NODE_IN_RANGE = new Node<>(POINT_IN_RANGE);

  private final static Point POINT_ENTITY_BLOCK = new Point(50, 50);
  private final static Node<Point> NODE_ENTITY_BLOCK = new Node<>(POINT_ENTITY_BLOCK);

  private final static Rect MAP_RECT = new Rect(0, 0, 100, 100);
  private final static Rect ENTITY_RECT = new Rect(45, 45, 10, 10);
  private final Rect collisionShape = new Rect(0, 0, 10000, 10000);

  private final static Entity blockingEntity;
  private final static Set<Entity> blockingEntities = new HashSet<>();

  private final static Walkspeed WALKSPD = Walkspeed.fromFloat(1.0f);

  static {
    blockingEntity = Mockito.mock(Entity.class);
    blockingEntities.add(blockingEntity);
  }

  private TileNodeProvider provider;

  @Before
  public void setup() {

    when(gameMap.isWalkable(anyLong(), anyLong())).thenReturn(true);
    when(gameMap.getWalkspeed(anyLong(), anyLong())).thenReturn(WALKSPD);
    when(gameMap.getRect()).thenReturn(MAP_RECT);

    when(entityService.getComponent(blockingEntity, PositionComponent.class)).thenReturn(Optional.of(posComp));
    when(posComp.getShape()).thenReturn(collisionShape);

    provider = new TileNodeProvider(gameMap, entityService, entitySearchService);
  }

  @Test(expected = NullPointerException.class)
  public void ctor_nullMap_throws() {
    new TileNodeProvider(null, entityService, entitySearchService);
  }

  @Test(expected = NullPointerException.class)
  public void ctor_nullEntityService_throws() {
    new TileNodeProvider(gameMap, null, entitySearchService);
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
