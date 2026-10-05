package net.bestia.login.zone

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/** Stands in for a zone's HTTP port and records every call the login server makes to it. */
class ZoneStub : AutoCloseable {

  data class Call(
    val method: String,
    val path: String,
    val authorization: String?,
    val body: String
  )

  val calls: MutableList<Call> = CopyOnWriteArrayList()

  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
    createContext("/") { exchange ->
      calls.add(
        Call(
          method = exchange.requestMethod,
          path = exchange.requestURI.path,
          authorization = exchange.requestHeaders.getFirst("Authorization"),
          body = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
        )
      )
      exchange.sendResponseHeaders(204, -1)
      exchange.close()
    }
    start()
  }

  val endpoint: ZoneEndpoint
    get() = ZoneEndpoint(name = "stub-${server.address.port}", baseUrl = "http://127.0.0.1:${server.address.port}")

  override fun close() {
    server.stop(0)
  }
}
