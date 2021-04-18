package net.bestia.zoneserver.actor.entity.component

import akka.actor.ActorRef
import net.bestia.model.bestia.ConditionValues
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.actor.entity.ComponentUpdated
import net.bestia.zoneserver.actor.entity.SubscribeForComponentUpdates
import net.bestia.zoneserver.assertInstanceOf
import net.bestia.zoneserver.entity.component.ConditionComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class ConditionComponentActorTest : AbstractActorTest() {

  private val conditionComponent = ConditionComponent(1, ConditionValues())

  @Test
  fun `subscribes to StatusComponent changes`() {
    testKit {
      it.actorOf(ConditionComponentActor::class, conditionComponent)

      val msg = it.expectMsgClass(SubscribeForComponentUpdates::class.java)
      assertEquals(StatusComponent::class.java, msg.componentType)
    }
  }

  fun `after an update with a status value ticks the regeneration`() {

  }

  @Test
  fun `AddHp command adds hp`() {
    testKit {
      val sut = it.actorOf(ConditionComponentActor::class, conditionComponent)
      it.receiveN(1)

      val cmd = AddHp(1, 10)
      sut.tell(cmd, ActorRef.noSender())

      val component = it.expectMsgClass(ComponentUpdated::class.java).component

      assertInstanceOf<ConditionComponent>(component).let { c ->
        assertEquals(10, c.conditionValues.currentHealth)
      }
    }
  }
}

