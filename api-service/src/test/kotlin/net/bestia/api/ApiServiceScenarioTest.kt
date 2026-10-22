package net.bestia.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import net.bestia.api.push.PushSubscription
import net.bestia.api.push.WebPushSender
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusRequest
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneEvent
import net.bestia.bus.contract.ZoneQuery
import net.bestia.bus.valkey.ValkeyEventPublisher
import net.bestia.bus.valkey.ValkeyRequestServer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Date
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The api-service end to end against a stand-in zone: a request server on the real bus whose answers each test
 * sets, and an event publisher with the zone's own Valkey user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiTestContainers::class, ApiServiceScenarioTest.RecordingPush::class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiServiceScenarioTest {

  @TestConfiguration(proxyBeanMethods = false)
  class RecordingPush {
    val sent = CopyOnWriteArrayList<Pair<Long, String>>()

    @Bean
    @Primary
    fun recordingSender(): WebPushSender {
      return WebPushSender { subscription: PushSubscription, payload: ByteArray ->
        sent += subscription.accountId to String(payload)
        WebPushSender.Outcome.DELIVERED
      }
    }
  }

  @Autowired
  private lateinit var rest: TestRestTemplate

  @Autowired
  private lateinit var push: RecordingPush

  @LocalServerPort
  private var port: Int = 0

  private val mapper = jacksonObjectMapper()
  private lateinit var zoneClient: RedisClient
  private lateinit var zone: ValkeyRequestServer
  private lateinit var events: ValkeyEventPublisher

  @Volatile
  private var answer: (BusRequest) -> BusReply = { BusReply.of(it.correlationId, BusReply.Status.NOT_FOUND) }
  private val asked = CopyOnWriteArrayList<BusRequest>()

  @BeforeAll
  fun startZone() {
    val uri = RedisURI.Builder.redis(ApiTestContainers.valkey.host, ApiTestContainers.valkey.getMappedPort(6379))
      .withAuthentication("zone", "zone-dev-password".toCharArray())
      .build()
    zoneClient = RedisClient.create(uri)
    zone = ValkeyRequestServer(zoneClient, "zone-1", { request -> asked += request; answer(request) })
    events = ValkeyEventPublisher(zoneClient)
  }

  @AfterAll
  fun stopZone() {
    events.close()
    zone.close()
    zoneClient.shutdown()
  }

  @BeforeEach
  fun reset() {
    asked.clear()
    push.sent.clear()
    answer = { BusReply.of(it.correlationId, BusReply.Status.NOT_FOUND) }
  }

  @Test
  fun `a call without a companion token is refused`() {
    assertEquals(401, rest.getForEntity("/v1/companion/masters", String::class.java).statusCode.value())
    assertEquals(401, get("/v1/companion/masters", token = "not-a-token").statusCode.value())
  }

  @Test
  fun `the account's masters come from the zone, asked on its behalf`() {
    answer = { BusReply.ok(it.correlationId, ZoneAnswer.Masters(listOf(ZoneAnswer.Masters.Master(5, 1, "Ayla", 12, "Ashfen", false)))) }

    val body = json(get("/v1/companion/masters", accountId = 41))

    assertEquals("Ayla", body[0]["name"].asText())
    assertEquals(41L, asked.single().accountId)
  }

  @Test
  fun `the zone's refusals become HTTP statuses`() {
    answer = { BusReply.of(it.correlationId, BusReply.Status.NOT_FOUND) }
    assertEquals(404, get("/v1/companion/masters/9", accountId = 42).statusCode.value())

    answer = { BusReply.of(it.correlationId, BusReply.Status.BUSY) }
    val busy = get("/v1/companion/masters/9/bestias", accountId = 42)
    assertEquals(503, busy.statusCode.value())
    assertEquals("2", busy.headers.getFirst(HttpHeaders.RETRY_AFTER))

    answer = {
      Thread.sleep(3_000)
      BusReply.of(it.correlationId, BusReply.Status.NOT_FOUND)
    }
    assertEquals(504, get("/v1/companion/masters/9/inventory", accountId = 42).statusCode.value())
  }

  @Test
  fun `a repeated standing order runs once in the zone`() {
    val runs = AtomicInteger()
    answer = { request ->
      runs.incrementAndGet()
      val order = request.query as ZoneQuery.SetStance
      BusReply.ok(request.correlationId, ZoneAnswer.Stance(order.playerBestiaId, order.stance.uppercase(), 100))
    }
    val key = UUID.randomUUID().toString()

    val first = put("/v1/companion/masters/5/bestias/7/stance", mapOf("stance" to "hold", "aggression" to 500), 43, key)
    val second = put("/v1/companion/masters/5/bestias/7/stance", mapOf("stance" to "hold", "aggression" to 500), 43, key)

    assertEquals(200, first.statusCode.value())
    assertEquals(json(first), json(second))
    assertEquals("HOLD", json(first)["stance"].asText())
    assertEquals(1, runs.get())
  }

  @Test
  fun `a tile crosses the bus once and is then revalidated`() {
    val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    answer = { request ->
      val query = request.query as ZoneQuery.MapTile
      if (query.ifNoneMatch == "\"e1\"") {
        BusReply(request.correlationId, BusReply.Status.NOT_MODIFIED, ZoneAnswer.Tile("\"e1\"", shared = false))
      } else {
        BusReply.ok(request.correlationId, ZoneAnswer.Tile("\"e1\"", shared = false), png)
      }
    }

    val first = rest.exchange("/v1/companion/masters/5/map/t/3/1/2.png", HttpMethod.GET, entity(44), ByteArray::class.java)
    assertEquals(200, first.statusCode.value())
    assertContentEquals(png, first.body)
    assertEquals("\"e1\"", first.headers.eTag)

    val conditional = rest.exchange(
      "/v1/companion/masters/5/map/t/3/1/2.png",
      HttpMethod.GET,
      entity(44, mapOf(HttpHeaders.IF_NONE_MATCH to "\"e1\"")),
      ByteArray::class.java,
    )
    assertEquals(304, conditional.statusCode.value())

    // A browser without the copy is answered from this service's cache, after a 304 from the zone.
    val fromCache = rest.exchange("/v1/companion/masters/5/map/t/3/1/2.png", HttpMethod.GET, entity(44), ByteArray::class.java)
    assertContentEquals(png, fromCache.body)
    assertEquals("\"e1\"", (asked.last().query as ZoneQuery.MapTile).ifNoneMatch)
  }

  @Test
  fun `a game client's ticket opens the map as its account, and nothing else`() {
    answer = { request ->
      when (val query = request.query) {
        is ZoneQuery.WhoseTicket ->
          if (query.ticket == "live-ticket") BusReply.ok(request.correlationId, ZoneAnswer.TicketOwner(47))
          else BusReply.of(request.correlationId, BusReply.Status.NOT_FOUND)
        ZoneQuery.MapMeta -> BusReply.ok(request.correlationId, ZoneAnswer.MapInfo("w", 256, 0, 9, 128000.0, 128000.0, 1.0))
        else -> BusReply.ok(request.correlationId, ZoneAnswer.Masters(emptyList()))
      }
    }

    val meta = rest.exchange("/v1/companion/masters/5/map/meta", HttpMethod.GET, ticketed("live-ticket"), String::class.java)
    assertEquals(200, meta.statusCode.value())
    assertEquals(1.0, json(meta)["metresPerVoxel"].asDouble())
    assertEquals(47L, asked.last().accountId, "the map was asked for as the ticket's account")

    assertEquals(401, rest.exchange("/v1/companion/masters/5/map/meta", HttpMethod.GET, ticketed("dead-ticket"), String::class.java).statusCode.value())
    // A ticket is no companion token: everything off the map routes refuses it, however the path is spelled.
    for (path in listOf("/v1/companion/masters", "/v1/companion/masters/5/bestias", "/v1/companion/masters/5/map/../bestias")) {
      assertEquals(401, rest.exchange(path, HttpMethod.GET, ticketed("live-ticket"), String::class.java).statusCode.value(), path)
    }
  }

  @Test
  fun `services are a live answer, never cached for keeps`() {
    answer = { request ->
      val query = request.query as ZoneQuery.MapServices
      if (query.ifNoneMatch == "\"s1\"") {
        BusReply(request.correlationId, BusReply.Status.NOT_MODIFIED, ZoneAnswer.Services("\"s1\"", emptyList()))
      } else {
        BusReply.ok(request.correlationId, ZoneAnswer.Services("\"s1\"", listOf(ZoneAnswer.Services.Service(9, "INN", "The Boar", 1.0, 2.0, "Ashfen", 6, 22))))
      }
    }

    val first = rest.exchange("/v1/companion/masters/5/map/s/2/1/1", HttpMethod.GET, entity(48), String::class.java)
    assertEquals(200, first.statusCode.value())
    assertEquals("INN", json(first)["services"][0]["kind"].asText())
    assertTrue(first.headers.cacheControl!!.contains("no-cache") && first.headers.cacheControl!!.contains("private"))

    val again = rest.exchange("/v1/companion/masters/5/map/s/2/1/1", HttpMethod.GET, entity(48, mapOf(HttpHeaders.IF_NONE_MATCH to "\"s1\"")), String::class.java)
    assertEquals(304, again.statusCode.value())
  }

  @Test
  fun `an emblem crosses the bus once, is immutable, and opens to a game client's ticket`() {
    val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    val hash = "ab".repeat(32)
    answer = { request ->
      when (val query = request.query) {
        is ZoneQuery.WhoseTicket -> BusReply.ok(request.correlationId, ZoneAnswer.TicketOwner(50))
        is ZoneQuery.GuildEmblem -> BusReply.ok(request.correlationId, ZoneAnswer.Emblem(query.hash), png)
        else -> BusReply.of(request.correlationId, BusReply.Status.NOT_FOUND)
      }
    }

    val first = rest.exchange("/v1/companion/emblems/$hash.png", HttpMethod.GET, ticketed("live-ticket"), ByteArray::class.java)
    assertEquals(200, first.statusCode.value())
    assertContentEquals(png, first.body)
    assertTrue(first.headers.cacheControl!!.contains("immutable"))
    assertEquals("nosniff", first.headers.getFirst("X-Content-Type-Options"))

    val again = rest.exchange("/v1/companion/emblems/$hash.png", HttpMethod.GET, entity(50), ByteArray::class.java)
    assertContentEquals(png, again.body)
    assertEquals(1, asked.count { it.query is ZoneQuery.GuildEmblem })

    assertEquals(400, rest.exchange("/v1/companion/emblems/not-a-hash.png", HttpMethod.GET, entity(50), String::class.java).statusCode.value())
  }

  @Test
  fun `a fully charted tile is public and immutable`() {
    answer = { BusReply.ok(it.correlationId, ZoneAnswer.Tile("\"shared\"", shared = true), byteArrayOf(1, 2, 3)) }

    val tile = rest.exchange("/v1/companion/masters/5/map/t/9/0/0.png", HttpMethod.GET, entity(49), ByteArray::class.java)

    val cacheControl = tile.headers.cacheControl!!
    assertTrue(cacheControl.contains("public") && cacheControl.contains("immutable"), cacheControl)
  }

  @Test
  fun `a habitat overlay opens to a game client's ticket and is never public`() {
    answer = { request ->
      when (val query = request.query) {
        is ZoneQuery.WhoseTicket -> BusReply.ok(request.correlationId, ZoneAnswer.TicketOwner(50))
        is ZoneQuery.HabitatTile ->
          BusReply.ok(request.correlationId, ZoneAnswer.Tile("\"h${query.speciesId}\"", shared = true), byteArrayOf(1, 2, 3))
        else -> BusReply.of(request.correlationId, BusReply.Status.NOT_FOUND)
      }
    }

    val tile = rest.exchange("/v1/companion/masters/5/map/h/1/2/3/-4.png", HttpMethod.GET, ticketed("t"), ByteArray::class.java)

    assertEquals(200, tile.statusCode.value())
    assertEquals(ZoneQuery.HabitatTile(5, 1, 2, 3, -4, null), asked.last().query)
    val cacheControl = tile.headers.cacheControl!!
    assertTrue(cacheControl.contains("private") && cacheControl.contains("no-cache"), cacheControl)
  }

  @Test
  fun `the socket carries the account's events, including those it missed`() {
    val frames = CopyOnWriteArrayList<JsonNode>()
    val socket = connect(45, lastEventId = null, frames)
    await().atMost(Duration.ofSeconds(5)).until { frames.any { it["type"].asText() == "ready" } }
    val lastSeen = frames.first { it["type"].asText() == "ready" }["lastEventId"].asText()

    events.publish(ZoneEvent.StanceChanged(accountId = 45, masterId = 5, playerBestiaId = 7, stance = "HOLD", aggression = 20))
    events.publish(ZoneEvent.StanceChanged(accountId = 99, masterId = 6, playerBestiaId = 8, stance = "HOLD", aggression = 20))

    await().atMost(Duration.ofSeconds(5)).until { frames.any { it["type"].asText() == "event" } }
    val event = frames.first { it["type"].asText() == "event" }["event"]
    assertEquals("StanceChanged", event["type"].asText())
    assertEquals(7, event["data"]["playerBestiaId"].asInt())
    assertTrue(event["data"]["accountId"] == null)
    socket.close()

    val replayed = CopyOnWriteArrayList<JsonNode>()
    connect(45, lastEventId = lastSeen, replayed).use {
      await().atMost(Duration.ofSeconds(5)).until { replayed.any { it["type"].asText() == "ready" } }
      assertEquals(1, replayed.count { it["type"].asText() == "event" })
    }
  }

  @Test
  fun `an account away from the game is notified once per event it wants`() {
    val subscription = mapOf(
      "endpoint" to "https://fcm.googleapis.com/fcm/send/${UUID.randomUUID()}",
      "keys" to mapOf("p256dh" to "BPC8EVmzvqSQvPRBDi7FjAQqyAJGZsxH9jKT16aTDbUK79Vg4h1rwiPXhadB5_X6i1fOj0--Na1QGZJdQgNlfko", "auth" to "AAAAAAAAAAAAAAAAAAAAAA"),
    )
    assertEquals(201, post("/v1/companion/push/subscriptions", subscription, accountId = 46).statusCode.value())
    val internal = mapOf("endpoint" to "https://10.0.0.1/steal", "keys" to subscription["keys"])
    assertEquals(400, post("/v1/companion/push/subscriptions", internal, accountId = 46).statusCode.value())

    events.publish(ZoneEvent.HomeFell(accountId = 46, masterId = 5, masterName = "Ayla", fallen = "Ashfen", newHome = "Brook"))
    events.publish(ZoneEvent.StanceChanged(accountId = 46, masterId = 5, playerBestiaId = 7, stance = "HOLD", aggression = 1))

    await().atMost(Duration.ofSeconds(10)).until { push.sent.isNotEmpty() }
    Thread.sleep(500)
    assertEquals(1, push.sent.size)
    assertTrue(push.sent.single().second.contains("Ashfen has fallen"))
  }

  @Test
  fun `a chat line from the app is said in the zone, and its refusals become statuses`() {
    var refusal: String? = null
    answer = { BusReply.ok(it.correlationId, ZoneAnswer.ChatSent(refusal)) }
    val line = mapOf("channel" to "PARTY", "text" to "on my way")

    assertEquals(204, post("/v1/companion/masters/5/chat", line, accountId = 50, idempotencyKey = UUID.randomUUID().toString()).statusCode.value())
    assertEquals(ZoneQuery.SendChat(5, "PARTY", "on my way"), asked.single().query)
    assertEquals(50L, asked.single().accountId)

    refusal = ZoneAnswer.ChatSent.NOT_IN_PARTY
    assertEquals(409, post("/v1/companion/masters/5/chat", line, accountId = 50, idempotencyKey = UUID.randomUUID().toString()).statusCode.value())

    refusal = ZoneAnswer.ChatSent.CHAT_LOCKED
    assertEquals(403, post("/v1/companion/masters/5/chat", line, accountId = 50, idempotencyKey = UUID.randomUUID().toString()).statusCode.value())
  }

  @Test
  fun `chat reaches the socket and is never pushed`() {
    val subscription = mapOf(
      "endpoint" to "https://fcm.googleapis.com/fcm/send/${UUID.randomUUID()}",
      "keys" to mapOf("p256dh" to "BPC8EVmzvqSQvPRBDi7FjAQqyAJGZsxH9jKT16aTDbUK79Vg4h1rwiPXhadB5_X6i1fOj0--Na1QGZJdQgNlfko", "auth" to "AAAAAAAAAAAAAAAAAAAAAA"),
    )
    assertEquals(201, post("/v1/companion/push/subscriptions", subscription, accountId = 51).statusCode.value())
    val frames = CopyOnWriteArrayList<JsonNode>()
    connect(51, lastEventId = null, frames).use {
      await().atMost(Duration.ofSeconds(5)).until { frames.any { it["type"].asText() == "ready" } }

      events.publish(ZoneEvent.ChatLine(accountId = 51, masterId = 5, channel = "GUILD", senderName = "Ayla", text = "hi", sentAtEpochMs = 1))

      await().atMost(Duration.ofSeconds(5)).until { frames.any { it["type"].asText() == "event" } }
      val event = frames.first { it["type"].asText() == "event" }["event"]
      assertEquals("ChatLine", event["type"].asText())
      assertEquals("hi", event["data"]["text"].asText())
      assertEquals("GUILD", event["data"]["channel"].asText())
    }
    Thread.sleep(500)
    assertTrue(push.sent.none { it.first == 51L })
  }

  private fun connect(accountId: Long, lastEventId: String?, frames: MutableList<JsonNode>): WebSocketSession {
    val handler = object : TextWebSocketHandler() {
      override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        frames.add(mapper.readTree(message.payload))
      }
    }
    val socket = StandardWebSocketClient().execute(handler, "ws://localhost:$port/v1/companion/ws").get(5, TimeUnit.SECONDS)
    socket.sendMessage(TextMessage(mapper.writeValueAsString(mapOf("type" to "auth", "token" to token(accountId), "lastEventId" to lastEventId))))
    return socket
  }

  private fun get(path: String, accountId: Long = 1, token: String = token(accountId)): ResponseEntity<String> {
    return rest.exchange(path, HttpMethod.GET, HttpEntity<Void>(headers(token)), String::class.java)
  }

  private fun post(path: String, body: Any, accountId: Long, idempotencyKey: String? = null): ResponseEntity<String> {
    val headers = headers(token(accountId)).apply { idempotencyKey?.let { set("Idempotency-Key", it) } }
    return rest.exchange(path, HttpMethod.POST, HttpEntity(body, headers), String::class.java)
  }

  private fun put(path: String, body: Any, accountId: Long, idempotencyKey: String): ResponseEntity<String> {
    val headers = headers(token(accountId)).apply { set("Idempotency-Key", idempotencyKey) }
    return rest.exchange(path, HttpMethod.PUT, HttpEntity(body, headers), String::class.java)
  }

  private fun entity(accountId: Long, extra: Map<String, String> = emptyMap()): HttpEntity<Void> {
    return HttpEntity(headers(token(accountId)).apply { extra.forEach { (name, value) -> set(name, value) } })
  }

  private fun ticketed(ticket: String): HttpEntity<Void> {
    return HttpEntity(HttpHeaders().apply { set(HttpHeaders.AUTHORIZATION, "Ticket $ticket") })
  }

  private fun headers(token: String): HttpHeaders {
    return HttpHeaders().apply {
      setBearerAuth(token)
      contentType = MediaType.APPLICATION_JSON
    }
  }

  private fun json(response: ResponseEntity<String>): JsonNode {
    return mapper.readTree(response.body)
  }

  /** Minted as login-server mints it. */
  private fun token(accountId: Long): String {
    val key = Keys.hmacShaKeyFor("api-test-companion-secret-long-enough-for-hmac".toByteArray(StandardCharsets.UTF_8))
    return Jwts.builder()
      .subject(accountId.toString())
      .issuer("login")
      .audience().add("companion").and()
      .expiration(Date(System.currentTimeMillis() + 600_000))
      .signWith(key)
      .compact()
  }

  companion object {
    @JvmStatic
    @DynamicPropertySource
    fun bus(registry: DynamicPropertyRegistry) {
      registry.add("bus.host") { ApiTestContainers.valkey.host }
      registry.add("bus.port") { ApiTestContainers.valkey.getMappedPort(6379) }
    }
  }
}
