package net.bestia.zoneserver.actor.entity

import akka.actor.ActorRef
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.actor.entity.component.EntityComponentActorFactory
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.LivetimeComponent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant

@ExtendWith(MockitoExtension::class)
internal class EntityActorTest : AbstractActorTest() {

  @Mock
  private lateinit var factory: EntityComponentActorFactory

  @Test
  fun `component update subscriptions forward messages of suitable component updates`() {
    testKit {
      val sut = it.actorOf(EntityActor::class, factory)
      sut.tell(NewEntity(Entity(1)), ActorRef.noSender())

      sut.tell(SubscribeForComponentUpdates(LevelComponent::class.java, it.ref), ActorRef.noSender())
      sut.tell(ComponentUpdated(LivetimeComponent(1, Instant.now().plusSeconds(60))), ActorRef.noSender())

      it.expectNoMessage()

      val updateMsg = ComponentUpdated(LevelComponent(1, 10, 0))
      sut.tell(updateMsg, ActorRef.noSender())
      it.expectMsg(updateMsg)
    }
  }

  @Test
  fun `terminated child component actor dont receives updates via subscriptions anymore`() {

  }

  @Test
  fun `EntityRequest messages are properly responded with Entity contents`() {

  }

  @Test
  fun `a uninitialized EntityActor respond to EntityRequest with EntityDoesNotExist and terminates`() {
    testKit {
      val sut = testActorOf(EntityActor::class, factory)
      sut.tell(EntityRequest(1, it.ref), ActorRef.noSender())
      it.expectMsg(EntityDoesNotExist)
      assertTrue(sut.isTerminated)
    }
  }

  @Test
  fun `a new EntityActor only awaits a NewEntity message otherwise terminates`() {
    testKit {
      val expectedTermination = testActorOf(EntityActor::class, factory)
      expectedTermination.tell("", ActorRef.noSender())
      assertTrue(expectedTermination.isTerminated)

      val noExpectedTermination = testActorOf(EntityActor::class, factory)
      expectedTermination.tell(NewEntity(Entity(1)), ActorRef.noSender())
      assertFalse(noExpectedTermination.isTerminated)
    }
  }

}