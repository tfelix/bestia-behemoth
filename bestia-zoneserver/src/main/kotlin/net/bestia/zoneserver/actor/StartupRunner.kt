package net.bestia.zoneserver.actor

import akka.NotUsed
import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.Http
import akka.http.javadsl.model.ws.Message
import akka.http.javadsl.model.ws.TextMessage
import akka.http.javadsl.server.AllDirectives
import akka.http.javadsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.OverflowStrategy
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import mu.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

internal class Translator : AbstractActor() {

  private var outActor: ActorRef? = null

  override fun createReceive(): Receive {
    return receiveBuilder()
            .match(String::class.java, this::processMessage)
            .match(ActorRef::class.java, this::handleInit)
            .matchAny(this::processAnyMessage)
            .build()
  }

  private fun handleInit(outActor: ActorRef) {
    this.outActor = outActor
    outActor.tell("Hello Client!!!", self)
  }

  private fun processAnyMessage(msg: Any) {
    println("Received: $msg")
    sender.tell(ACK, self)
  }

  private fun processMessage(msg: String) {
    when(msg) {
      INIT -> {
        println("Init")
        sender.tell(ACK, self)
      }
      COMPLETE -> {
        println("Stream complete")
      }
      else -> {
        println("Received: $msg")
        sender.tell(ACK, self)
      }
    }
  }

  companion object {
    fun props(): Props {
      return Props.create(Translator::class.java, { Translator() })
    }

    const val ACK = "onAck"
    const val INIT = "onInit"
    const val COMPLETE = "onComplete"
  }
}



class WebSocketRouter(
        private val system: ActorSystem
) : AllDirectives() {

  private fun socketFlow(): Flow<Message, Message, NotUsed> {
    val connectionActor = system.actorOf(Translator.props())

    val incomingMessages = Sink.actorRefWithAck<Message>(connectionActor,
            Translator.INIT,
            Translator.ACK,
            Translator.COMPLETE
    ) { _ -> "Failure" }

    val outgoingMessages: Source<Message, *> = Source.actorRef<String>(100, OverflowStrategy.fail())
            .mapMaterializedValue { outActor ->
              connectionActor.tell(outActor, ActorRef.noSender())
            }.map { outMsg -> TextMessage.create(outMsg) }

    return Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  fun createRoute(): Route {
    return route(
            path("socket", {
              handleWebSocketMessages(socketFlow())
            })
    )
  }
}

@Component
class StartupRunner(
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
