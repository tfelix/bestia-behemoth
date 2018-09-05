package net.bestia.zoneserver.actor

import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.test.TestHazelcastInstanceFactory
import com.typesafe.config.ConfigFactory
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import scala.concurrent.duration.FiniteDuration
import java.time.Duration

fun Int.seconds(): Duration {
  return Duration.ofSeconds(this.toLong())
}

fun Duration.toScala(): FiniteDuration {
  return scala.concurrent.duration.Duration.fromNanos(this.toNanos())
}

@SpringBootTest
@RunWith(SpringRunner::class)
@ActiveProfiles("test")
abstract class BaseActorTest {

  @Configuration
  // @ComponentScan(basePackages = ["net.bestia"], excludeFilters = [ComponentScan.Filter(type = FilterType.REGEX, pattern = ["net\\.bestia\\.zoneserver\\.actor\\..*"])])
  class ActorTestConfiguration {
    @Bean
    @Primary
    fun hazelcastMock(): HazelcastInstance {
      val hzFact = TestHazelcastInstanceFactory()
      return hzFact.newHazelcastInstance()
    }
  }

  private val akkaConfig = ConfigFactory.load("akka-test")
  protected lateinit var system: ActorSystem

  @Autowired
  private lateinit var applicationContext: ApplicationContext

  @Before
  fun setup() {
    system = ActorSystem.create("test-system", akkaConfig)
    SpringExtension.initialize(system, applicationContext)
  }

  @After
  fun teardown() {
    TestKit.shutdownActorSystem(system)
  }
}