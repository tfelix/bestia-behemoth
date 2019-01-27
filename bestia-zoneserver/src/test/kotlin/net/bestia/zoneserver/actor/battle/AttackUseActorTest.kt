package net.bestia.zoneserver.actor.battle

import akka.testkit.javadsl.TestKit
import net.bestia.messages.attack.AttackUseMessage
import net.bestia.messages.entity.EntitySkillUseMessage
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.actor.SpringExtension
import org.junit.jupiter.api.Test

class AttackUseActorTest : AbstractActorTest() {

  @Test
  fun `Receiving AttackUseMessage transforms and replies it`() {
    object : TestKit(system) {
      init {
        val msg = AttackUseMessage(
            accountId = 1,
            attackId = 2,
            targetEntityId = 10,
            x = 0,
            y = 0
        )

        val sut = SpringExtension.actorOf(system, AttackUseActor::class.java)
        sut.tell(msg, ref)
        expectMsg(EntitySkillUseMessage::class.java)
      }
    }
  }
}