package net.bestia.zoneserver.map.path

import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.geometry.Point
import net.bestia.model.geometry.Rect
import net.bestia.model.map.BestiaMap
import net.bestia.model.map.Walkspeed
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.component.PositionComponent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class TileNodeProviderTest {

  @Mock
  private lateinit var gameMap: BestiaMap

  @Mock
  private lateinit var posComp: PositionComponent

  @Mock
  private lateinit var entityCollisionService: EntityCollisionService

  private val collisionShape = Rect(0, 0, 10000, 10000)
  private lateinit var provider: TileNodeProvider

  @BeforeEach
  fun setup() {
    provider = TileNodeProvider(gameMap, entityCollisionService)
  }

  @Test
  fun getConnectedNodes_nodeInMapRange_allConnections() {
    whenever(gameMap.isWalkable(anyLong(), anyLong())).thenReturn(true)
    whenever(gameMap.getWalkspeed(anyLong(), anyLong())).thenReturn(WALKSPD)
    whenever(gameMap.rect).thenReturn(MAP_RECT)

    val cons = provider.getConnectedNodes(NODE_IN_RANGE)
    assertFalse(cons.isEmpty())
  }

  @Test
  fun getConnectedNodes_nodeOutOfMapRange_emptyConnections() {
    whenever(gameMap.isWalkable(anyLong(), anyLong())).thenReturn(true)

    val cons = provider.getConnectedNodes(NODE_OUT_OF_RANGE)
    assertTrue(cons.isEmpty())
  }

  @Test
  fun getConnectedNodes_nodeBlockedByEntity_notGivenAsConnection() {
    whenever(gameMap.isWalkable(anyLong(), anyLong())).thenReturn(true)
    whenever(gameMap.getWalkspeed(anyLong(), anyLong())).thenReturn(WALKSPD)
    whenever(gameMap.rect).thenReturn(MAP_RECT)

    val cons = provider.getConnectedNodes(NODE_ENTITY_BLOCK)
    assertTrue(cons.isEmpty())
  }

  companion object {
    private val POINT_OUT_OF_RANGE = Point(1000, 1000)
    private val NODE_OUT_OF_RANGE = Node(POINT_OUT_OF_RANGE)

    private val POINT_IN_RANGE = Point(10, 10)
    private val NODE_IN_RANGE = Node(POINT_IN_RANGE)

    private val POINT_ENTITY_BLOCK = Point(50, 50)
    private val NODE_ENTITY_BLOCK = Node(POINT_ENTITY_BLOCK)

    private val MAP_RECT = Rect(0, 0, 100, 100)

    private val WALKSPD = Walkspeed(1.0f)
  }
}
