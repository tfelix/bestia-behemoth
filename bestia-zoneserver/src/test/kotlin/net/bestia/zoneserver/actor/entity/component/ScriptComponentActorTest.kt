package net.bestia.zoneserver.actor.entity.component

import akka.actor.ActorRef
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.entity.component.ScriptComponent
import net.bestia.zoneserver.script.ScriptService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.Duration

class ScriptComponentActorTest : AbstractActorTest() {

  @MockBean
  lateinit var scriptService: ScriptService

  @Test
  fun `SetIntervalCommand will setup an intervall and call the designated scripts`() {
    testKit {
      val scriptComp = ScriptComponent(1)
      val script = testActorOf(ScriptComponentActor::class, scriptComp)

      val cmd = SetIntervalCommand(
          entityId = 1,
          uuid = "abcd",
          timeout = Duration.ofMillis(100),
          callbackFn = "attack::bla"
      )

      script.tell(cmd, ActorRef.noSender())

      it.within(Duration.ofSeconds(1)) {
        verify(scriptService).execute(any())
      }
    }
  }
}