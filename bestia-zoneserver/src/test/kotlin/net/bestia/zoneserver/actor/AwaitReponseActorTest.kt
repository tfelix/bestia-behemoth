package net.bestia.zoneserver.actor

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.time.Duration

class AwaitReponseActorTest {

  @Test
  fun `triggers callback if all responses have been gathered`() {
    object : TestKit(system) {
      init {
        val testString = "helloWorld"
        val testLong = 42L

        var wasCalledCorrectly = false
        val awaitResponseProps = AwaitResponseActor.props(listOf(String::class, Long::class)) {
          wasCalledCorrectly = it.getReponse(String::class) == testString && it.getReponse(Long::class) == testLong
        }
        val actor = system.actorOf(awaitResponseProps)

        actor.tell(testString, ActorRef.noSender())
        actor.tell(testLong, ActorRef.noSender())

        awaitCond(Duration.ofSeconds(3)) {
          wasCalledCorrectly
        }
      }
    }
  }

  companion object {
    private lateinit var system: ActorSystem

    @BeforeClass
    @JvmStatic
    fun setup() {
      system = ActorSystem.create()
    }

    @AfterClass
    @JvmStatic
    fun teardown() {
      TestKit.shutdownActorSystem(system)
    }
  }
}