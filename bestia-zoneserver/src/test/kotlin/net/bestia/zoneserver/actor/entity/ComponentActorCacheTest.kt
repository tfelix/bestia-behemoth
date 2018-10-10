package net.bestia.zoneserver.actor.entity

import net.bestia.model.geometry.Point
import net.bestia.zoneserver.entity.component.MoveComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ComponentActorCacheTest {

  lateinit var cache: EntityActor.ComponentActorCache


  private val components = listOf(
      MoveComponent(1),
      PositionComponent(1, Point(1, 1))
  )

  @Before
  fun setup() {
    cache = EntityActor.ComponentActorCache()
    // cache.add(components[0], componetActorRef)
    // cache.add(components[1], componetActorRef)
  }

  @Test
  fun `receive function returns true when all is received`() {
    val awaitedComponentClasses = setOf(
        MoveComponent::class.java,
        PositionComponent::class.java
    )

    val hasAllResponses = { receivedResponses: List<Any> ->
      receivedResponses.asSequence()
          .map { it.javaClass }
          .toSet() == awaitedComponentClasses
    }

    Assert.assertTrue(hasAllResponses(components))
  }
}