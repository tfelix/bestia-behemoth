package net.bestia.zoneserver.actor.entity

import akka.actor.ActorRef
import com.nhaarman.mockito_kotlin.mock
import net.bestia.zoneserver.entity.component.MoveComponent
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ComponentActorCacheTest {

  lateinit var cache: EntityActor.ComponentActorCache

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  lateinit var componetActorRef: ActorRef

  @Before
  fun setup() {
    cache = EntityActor.ComponentActorCache()
    val moveComponent = MoveComponent(1)
    cache.add(moveComponent, componetActorRef)
  }

  @Test
  fun `allActors() returns all saved actors`() {
    Assert.assertEquals(listOf(componetActorRef), cache.allActors())
  }

  @Test
  fun `get() returns ActorRef for component`() {
    val comActor = cache.get(MoveComponent::class.java)
    Assert.assertEquals(componetActorRef, comActor)
  }

  @Test
  fun `getAllCachedComponentClasses() returns all cached classes`() {
    val allComponents = cache.getAllCachedComponentClasses()
    Assert.assertEquals(listOf(componetActorRef), cache.allActors())
  }
}