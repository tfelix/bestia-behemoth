package net.bestia.webserver

import akka.NotUsed
import akka.actor.ActorRef
import akka.actor.Props
import akka.http.javadsl.model.ws.Message
import akka.http.javadsl.model.ws.TextMessage
import akka.http.javadsl.server.HttpApp
import akka.http.javadsl.server.Route
import akka.stream.actor.AbstractActorPublisher
import akka.stream.actor.AbstractActorSubscriber
import akka.stream.actor.MaxInFlightRequestStrategy
import akka.stream.actor.RequestStrategy
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source


class WebSocketCommandSubscriber : AbstractActorSubscriber() {
  override fun createReceive(): Receive {
    return receiveBuilder().match(
            String::class.java, this::handleMessage)
            .build()
  }

  override fun requestStrategy(): RequestStrategy {
    return object : MaxInFlightRequestStrategy(10) {
      override fun inFlightInternally(): Int {
        // we do not hold any messages yet, but will eventually be
        // required, e.g. for request/response message handling
        return 0
      }
    }
  }

  private fun handleMessage(msg: String) {
    println(msg)
  }

  companion object {
    @JvmStatic
    fun props(): Props {
      return Props.create(WebSocketCommandSubscriber::class.java)
    }
  }
}

class WebSocketDataPublisher : AbstractActorPublisher<String>() {

  /*
  @Throws(Exception::class)
  override fun preStart() {
    for (eventClass in interesstedEvents) {
      // unsubscribing performed automatically by the event stream on actor destroy
      context.system().eventStream().subscribe(self(), eventClass)
    }
  }*/

  override fun createReceive(): Receive {
    return receiveBuilder().match(
            String::class.java, this::handleMessage)
            .build()
  }

  private fun handleMessage(message: Any) {

    // while the stream is not ready to receive data - incoming messages are lost

    if (isActive && totalDemand() > 0) {

      // val webSocketMessage = WebSocketMessage.create(message.javaClass.simpleName, message)

      //            System.out.println("send message to WS: " + message);

      onNext(message as String)

    } else {

      //            System.out.println("LOST message to WS: " + message);

    }

  }


  companion object {
    @JvmStatic
    fun props(): Props {
      return Props.create(WebSocketCommandSubscriber::class.java)
    }
  }
}

class WebsocketApp : HttpApp() {
  override fun routes(): Route {
    return get {
      path("socket") {
        handleWebSocketMessages(metrics())
      }
    }
  }

  private fun metrics(): Flow<Message, Message, NotUsed> {
    val metricsSink: Sink<Message, ActorRef> = Sink.actorSubscriber(WebSocketCommandSubscriber.props())
    val metricsSource: Source<Message, ActorRef> = Source.actorPublisher<Source<Message, ActorRef>>(WebSocketDataPublisher.props())
            .map({ _: Any -> TextMessage.create("Hello World") })
    // .map((measurementData) -> TextMessage.create(gson.toJson(measurementData)));
    return Flow.fromSinkAndSource(metricsSink, metricsSource)
  }
}

// @SpringBootApplication
object Application {

  @Throws(Exception::class)
  @JvmStatic
  fun main(args: Array<String>) {
    val myServer = WebsocketApp()
    myServer.startServer("localhost", 8080)
  }
}
