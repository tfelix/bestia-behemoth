package net.bestia.zoneserver.actor.connection

import akka.NotUsed
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.http.javadsl.model.ws.Message
import akka.http.javadsl.model.ws.TextMessage
import akka.http.javadsl.server.AllDirectives
import akka.http.javadsl.server.Route
import akka.stream.OverflowStrategy
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import net.bestia.zoneserver.actor.SpringExtension

class WebSocketRouter(
    private val system: ActorSystem
) : AllDirectives() {

  private fun socketFlow(): Flow<Message, Message, NotUsed> {
    val connectionActor = SpringExtension.actorOf(system, WebsocketActor::class.java)

    val incomingMessages = Sink.actorRefWithAck<Message>(connectionActor,
        WebsocketActor.INIT,
        WebsocketActor.ACK,
        WebsocketActor.COMPLETE
    ) { _ -> "Failure" }

    val outgoingMessages: Source<Message, *> = Source.actorRef<String>(100, OverflowStrategy.fail())
        .mapMaterializedValue { outActor ->
          connectionActor.tell(outActor, ActorRef.noSender())
        }.map { outMsg -> TextMessage.create(outMsg) }

    return Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  fun createRoute(): Route {
    return route(
        path("socket") {
          handleWebSocketMessages(socketFlow())
        }
    )
  }
}