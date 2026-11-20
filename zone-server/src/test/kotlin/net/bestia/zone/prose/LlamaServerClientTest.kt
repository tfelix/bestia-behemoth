package net.bestia.zone.prose

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The client against a small server that answers as llama.cpp does. */
class LlamaServerClientTest {

  private var asked = ""

  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
    createContext("/v1/chat/completions") { exchange ->
      asked = exchange.requestBody.readAllBytes().decodeToString()
      val answer = """{"choices":[{"message":{"role":"assistant","content":"Here lies Orwen."}}],
        |"timings":{"prompt_n":40,"prompt_per_second":36.0,"predicted_n":6,"predicted_per_second":5.6}}""".trimMargin().toByteArray()
      exchange.responseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, answer.size.toLong())
      exchange.responseBody.use { it.write(answer) }
    }
    createContext("/health") { exchange ->
      exchange.sendResponseHeaders(200, -1)
      exchange.close()
    }
    start()
  }

  private val client = LlamaServerClient("http://127.0.0.1:${server.address.port}", model = "test-model")

  @AfterEach
  fun stop() {
    server.stop(0)
  }

  @Test
  fun `a chat is sent with its sampling and the text of the answer comes back`() {
    val text = client.chat("Be exact.", "Write the epitaph of Orwen.", ProsePrompt.SAMPLING)

    assertEquals("Here lies Orwen.", text)
    assertTrue(""""role":"system","content":"Be exact."""" in asked, asked)
    assertTrue(""""temperature":0.6""" in asked && """"repeat_penalty":1.05""" in asked && """"max_tokens":400""" in asked, asked)
  }

  @Test
  fun `a server that is up is ready, one that is gone is not`() {
    assertTrue(client.isReady())

    server.stop(0)

    assertFalse(client.isReady())
  }
}
