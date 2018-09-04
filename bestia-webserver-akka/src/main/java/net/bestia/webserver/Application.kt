package net.bestia.webserver

import akka.NotUsed
import akka.actor.ActorSystem
import java.util.concurrent.atomic.AtomicInteger
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.ConnectionContext
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.javadsl.model.ws.Message
import akka.http.javadsl.model.ws.TextMessage
import akka.http.javadsl.model.ws.WebSocket
import akka.http.javadsl.model.ws.WebSocketRequest
import akka.http.javadsl.settings.ClientConnectionSettings
import akka.http.javadsl.settings.ServerSettings
import akka.japi.JavaPartialFunction
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Source
import akka.util.ByteString
import java.io.InputStreamReader
import java.io.BufferedReader
import java.util.*
import java.util.concurrent.TimeUnit

object WebSocketCoreExample {

  fun handleRequest(request: HttpRequest): HttpResponse {
    System.out.println("Handling request to " + request.uri)

    return if (request.uri.path() == "/greeter") {
      val greeterFlow = greeter()
      WebSocket.handleWebSocketRequestWith(request, greeterFlow)
    } else {
      HttpResponse.create().withStatus(404)
    }
  }

  /**
   * A handler that treats incoming messages as a name,
   * and responds with a greeting to that name
   */
  fun greeter(): Flow<Message, Message, NotUsed> {
    return Flow.create()
            .collect(object : JavaPartialFunction<Message, Message>() {
              @Throws(Exception::class)
              fun apply(msg: Message, isCheck: Boolean): Message? {
                return if (isCheck) {
                  if (msg.isText()) {
                    null
                  } else {
                    throw noMatch()
                  }
                } else {
                  handleTextMessage(msg.asTextMessage())
                }
              }
            })
  }

  fun handleTextMessage(msg: TextMessage): TextMessage {
    return if (msg.isStrict) {
      TextMessage.create("Hello " + msg.strictText)
    } else {
      TextMessage.create(Source.single("Hello ").concat(msg.streamedText))
    }
  }

  init {
    val system: ActorSystem? = null
    val materializer: ActorMaterializer? = null
    val handler: Flow<HttpRequest, HttpResponse, NotUsed>? = null
    val defaultSettings = ServerSettings.create(system)

    val pingCounter = AtomicInteger()

    val customWebsocketSettings = defaultSettings.websocketSettings
            .withPeriodicKeepAliveData({ ByteString.fromString(String.format("debug-%d", pingCounter.incrementAndGet())) })

    val customServerSettings = defaultSettings.withWebsocketSettings(customWebsocketSettings)

    val http = Http.get(system)
    http.bindAndHandle(handler,
            ConnectHttp.toHost("127.0.0.1"),
            customServerSettings, // pass the configuration
            system!!.log(),
            materializer)
  }

  init {
    val system: ActorSystem = ActorSystem.create()
    val materializer = ActorMaterializer.create(system)
    val clientFlow: Flow<Message, Message, NotUsed>? = null
    val defaultSettings = ClientConnectionSettings.create(system)

    val pingCounter = AtomicInteger()

    val customWebsocketSettings = defaultSettings.websocketSettings
            .withPeriodicKeepAliveData({ ByteString.fromString(String.format("debug-%d", pingCounter.incrementAndGet())) })

    val customSettings = defaultSettings.withWebsocketSettings(customWebsocketSettings)

    val http = Http.get(system)
    http.singleWebSocketRequest(
            WebSocketRequest.create("ws://127.0.0.1"),
            clientFlow,
            ConnectionContext.noEncryption(),
            Optional.empty(),
            customSettings,
            system.log(),
            materializer
    )
  }
}

// @SpringBootApplication
object Application {
  @Throws(Exception::class)
  @JvmStatic
  fun main(args: Array<String>) {

    try {
      val materializer = ActorMaterializer.create(system)

      val handler = { request -> WebSocketCoreExample.handleRequest(request) }
      val serverBindingFuture = Http.get(system).bindAndHandleSync(
              handler, ConnectHttp.toHost("localhost", 8080), materializer)

      // will throw if binding fails
      serverBindingFuture.toCompletableFuture().get(1, TimeUnit.SECONDS)
      println("Press ENTER to stop.")
      BufferedReader(InputStreamReader(System.`in`)).readLine()
    } finally {
      system.terminate()
    }
  }
}