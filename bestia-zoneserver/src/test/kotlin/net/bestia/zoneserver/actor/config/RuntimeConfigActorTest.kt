package net.bestia.zoneserver.actor.config

import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.config.RuntimeConfig
import net.bestia.zoneserver.config.RuntimeConfigService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import java.time.Duration

@Suppress("MapGetWithNotNullAssertionOperator")
internal class RuntimeConfigActorTest : AbstractActorTest() {

  @Autowired
  private lateinit var runtimeConfigServiceMock: RuntimeConfigService
  private lateinit var mediator: ActorRef

  @BeforeAll
  fun setup() {
    mediator = DistributedPubSub.get(system).mediator()
  }

  @Test
  fun `receiving RuntimeConfig via cluster PubSub will update them locally on the node`() {
    testKit {
      testActorOf(RuntimeConfigurationActor::class)

      val update = SaveRuntimeConfig(RuntimeConfig(MaintenanceLevel.FULL))
      mediator.tell(DistributedPubSubMediator.Publish(RuntimeConfigurationActor.TOPIC_RUNTIME_UPDATE, update), ActorRef.noSender())

      it.within(Duration.ZERO, Duration.ofSeconds(1)) {
        verify(runtimeConfigServiceMock).setConfigWithoutClusterUpdate(eq(update.newConfig))
      }
    }
  }

  @Test
  fun `receiving UpdateClusterRuntimeConfig will send them to the cluster PubSub`() {
    testKit {
      val sut = testActorOf(RuntimeConfigurationActor::class)

      val probes = injectProbeMembers(sut, listOf("mediator"))

      val update = RuntimeConfig(MaintenanceLevel.FULL)
      sut.tell(update, ActorRef.noSender())
      mediator.tell(DistributedPubSubMediator.Publish(RuntimeConfigurationActor.TOPIC_RUNTIME_UPDATE, update), ActorRef.noSender())

      probes["mediator"]!!.expectMsgClass(DistributedPubSubMediator.Publish::class.java)
    }
  }
}