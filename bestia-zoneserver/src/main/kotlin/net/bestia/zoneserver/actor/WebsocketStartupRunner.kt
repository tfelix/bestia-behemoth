package net.bestia.zoneserver.actor

import akka.actor.ActorSystem
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.Http
import akka.stream.ActorMaterializer
import mu.KotlinLogging
import net.bestia.zoneserver.actor.connection.WebSocketRouter
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
class WebsocketStartupRunner(
        private val system: ActorSystem
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    val materializer = ActorMaterializer.create(system)
    val http = Http.get(system)
    val router = WebSocketRouter(system)
    val routeFlow = router.createRoute().flow(system, materializer)
    http.bindAndHandle(routeFlow, ConnectHttp.toHost("localhost", 8090), materializer)
  }
}
