package net.bestia.zoneserver.config

import akka.actor.*
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import mu.KotlinLogging
import net.bestia.messages.MessageApi
import net.bestia.zoneserver.AkkaMessageApi
import net.bestia.zoneserver.actor.BestiaRootActor
import net.bestia.zoneserver.actor.SpringExtension
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.net.UnknownHostException

private val LOG = KotlinLogging.logger {  }

/**
 * Generates the akka configuration file which is used to connect to the remote
 * actor system of the bestia zone server. It overwrites the default akka config
 * file "application.conf" since this is already used by spring with
 * "akka.config".
 *
 * @author Thomas Felix
 */
@Configuration
class AkkaConfiguration : DisposableBean {

  private var systemInstance: ActorSystem? = null

  @Bean
  @Throws(UnknownHostException::class)
  fun actorSystem(appContext: ApplicationContext): ActorSystem? {

    val akkaConfig = ConfigFactory.load(AKKA_CONFIG_NAME)
    LOG.debug("Loaded akka config: {}.", akkaConfig.toString())

    systemInstance = ActorSystem.create("BestiaBehemoth", akkaConfig)
    val addr = Address("tcp", "BestiaBehemoth", "localhost", 6767)
    Cluster.get(systemInstance).join(addr)

    // initialize the application context in the Akka Spring extension.
    SpringExtension.initialize(systemInstance, appContext)

    return systemInstance
  }

  @Bean
  @Primary
  fun messageApi(system: ActorSystem): MessageApi {

    val typedProps = TypedProps(MessageApi::class.java, AkkaMessageApi::class.java)
    return TypedActor.get(system).typedActorOf(typedProps, "akkaMsgApi")
  }

  @Bean
  fun rootActor(msgApi: MessageApi): ActorRef {
    LOG.info("Starting actor system...")

    val rootActor = SpringExtension.actorOf(systemInstance, BestiaRootActor::class.java, msgApi)

    LOG.info("Bestia Zone startup completed.")

    return rootActor
  }

  /**
   * Kill the akka instance if it has been created and Spring shuts down.
   */
  @Throws(Exception::class)
  override fun destroy() {
    LOG.info("Stopping Akka instance.")

    if (systemInstance != null) {
      systemInstance!!.terminate()
    }
  }

  companion object {
    private const val AKKA_CONFIG_NAME = "akka"
  }
}
