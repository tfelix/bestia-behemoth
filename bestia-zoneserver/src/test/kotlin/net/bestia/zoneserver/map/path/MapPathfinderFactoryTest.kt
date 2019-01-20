package net.bestia.zoneserver.map.path

import net.bestia.zoneserver.entity.EntityService
import net.bestia.model.map.BestiaMap
import net.bestia.zoneserver.entity.EntityCollisionService
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class MapPathfinderFactoryTest {
  private lateinit var factory: MapPathfinderFactory

  @Mock
  private lateinit var entityService: EntityService

  @Mock
  private lateinit var entitiyCollisionService: EntityCollisionService

  @Mock
  private lateinit var map: BestiaMap

  @Before
  fun setup() {
    factory = MapPathfinderFactory(entitiyCollisionService)
  }

  @Test
  fun getPathfinder_validMap_validPathfinder() {
    val finder = factory.getPathfinder(map)
    Assert.assertNotNull(finder)
  }
}
