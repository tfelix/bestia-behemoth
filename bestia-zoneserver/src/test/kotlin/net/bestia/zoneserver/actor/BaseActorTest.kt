package net.bestia.zoneserver.actor

import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import net.bestia.zoneserver.TestZoneConfiguration
import org.junit.AfterClass
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
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
@Import(TestZoneConfiguration::class)
abstract class BaseActorTest {
  @Autowired
  protected lateinit var system: ActorSystem

  @AfterClass
  fun teardown() {
    TestKit.shutdownActorSystem(system)
  }
}