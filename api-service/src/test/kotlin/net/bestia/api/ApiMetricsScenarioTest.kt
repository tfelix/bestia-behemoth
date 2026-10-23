package net.bestia.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.micrometer.core.instrument.MeterRegistry
import net.bestia.bus.BusKeys
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The api-service's meters after real traffic: the same stand-in zone as [ApiServiceScenarioTest], and a read of the
 * context's own registry. The registry outlives a test, so every check compares against the value from before.
 */
class ApiMetricsScenarioTest : ApiScenarioTestBase() {

  @Autowired
  private lateinit var rest: TestRestTemplate

  @Autowired
  private lateinit var registry: MeterRegistry

  @Autowired
  private lateinit var push: ApiServiceScenarioTest.RecordingPush

  @LocalServerPort
  private var port: Int = 0

  private val mapper = jacksonObjectMapper()
  private lateinit var zoneClient: RedisClient
  private lateinit var zone: ValkeyRequestServer
  private lateinit var events: ValkeyEventPublisher

  @Volatile
  private var answer: (BusRequest) -> BusReply = { BusReply.of(it.correlationId, BusReply.Status.NOT_FOUND) }

  @BeforeAll
  fun startZone() {
    val uri = RedisURI.Builder.redis(ApiTestContainers.valkey.host, ApiTestContainers.valkey.getMappedPort(6379))
      .withAuthentication("zone", "zone-dev-password".toCharArray())
      .build()
    zoneClient = RedisClient.create(uri)
    zone = ValkeyRequestServer(zoneClient, "zone-1", { request -> answer(request) })
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
    answer = { BusReply.of(it.correlationId, BusReply.Status.NOT_FOUND) }
  }

  @Test
  fun `every ask is timed by its query and how it ended`() {
    val ok = ask("list_masters", "ok")
    val notFound = ask("master_sheet", "not_found")
    val busy = ask("bestia_roster", "busy")
    val asks = value("api.bus.asks")

    answer = { BusReply.ok(it.correlationId, ZoneAnswer.Masters(emptyList())) }
    assertEquals(200, get("/v1/companion/masters", accountId = 70).statusCode.value())
    answer = { BusReply.of(it.correlationId, BusReply.Status.NOT_FOUND) }
    assertEquals(404, get("/v1/companion/masters/9", accountId = 70).statusCode.value())
    answer = { BusReply.of(it.correlationId, BusReply.Status.BUSY) }
    assertEquals(503, get("/v1/companion/masters/9/bestias", accountId = 70).statusCode.value())

    assertEquals(ok + 1, ask("list_masters", "ok"))
    assertEquals(notFound + 1, ask("master_sheet", "not_found"))
    assertEquals(busy + 1, ask("bestia_roster", "busy"))
    assertEquals(asks + 3, value("api.bus.asks"))
  }

  @Test
  fun `a zone that does not answer in time is a timeout, and its late reply an orphan`() {
    val timeouts = value("api.bus.ask.timeouts")
    val timedOut = ask("inventory", "timeout")
    val orphans = value("api.bus.replies.dropped", "reason", "orphan")
    answer = {
      Thread.sleep(2_000)
      BusReply.of(it.correlationId, BusReply.Status.NOT_FOUND)
    }

    assertEquals(504, get("/v1/companion/masters/9/inventory", accountId = 71).statusCode.value())

    assertEquals(timedOut + 1, ask("inventory", "timeout"))
    assertEquals(timeouts + 1, value("api.bus.ask.timeouts"))
    await().atMost(Duration.ofSeconds(5)).untilAsserted {
      assertEquals(orphans + 1, value("api.bus.replies.dropped", "reason", "orphan"))
    }
  }

  @Test
  fun `a call the auth filter turns away is counted by what it presented and where it went`() {
    val none = rejected("none", "401", "masters")
    val badJwt = value("api.auth.token.failures", "reason", "bad_jwt")
    val bearer = rejected("bearer", "401", "masters")
    val expired = value("api.auth.token.failures", "reason", "expired")
    val audience = value("api.auth.token.failures", "reason", "wrong_audience")

    assertEquals(401, rest.getForEntity("/v1/companion/masters", String::class.java).statusCode.value())
    assertEquals(401, get("/v1/companion/masters", token = "not-a-token").statusCode.value())
    assertEquals(401, get("/v1/companion/masters", token = token(72, ttlMillis = -60_000)).statusCode.value())
    assertEquals(401, get("/v1/companion/masters", token = token(72, audience = "zone")).statusCode.value())

    assertEquals(none + 1, rejected("none", "401", "masters"))
    assertEquals(badJwt + 1, value("api.auth.token.failures", "reason", "bad_jwt"))
    assertEquals(expired + 1, value("api.auth.token.failures", "reason", "expired"))
    assertEquals(audience + 1, value("api.auth.token.failures", "reason", "wrong_audience"))
    assertEquals(bearer + 3, rejected("bearer", "401", "masters"))
  }

  @Test
  fun `a ticket is asked of the zone once, then remembered`() {
    val miss = value("api.auth.ticket.lookups", "outcome", "zone_miss")
    val hit = value("api.auth.ticket.lookups", "outcome", "zone_hit")
    val cached = value("api.auth.ticket.lookups", "outcome", "cache_hit")
    val refused = rejected("ticket", "401", "map_meta")
    answer = { request ->
      when (val query = request.query) {
        is ZoneQuery.WhoseTicket ->
          if (query.ticket == "metrics-live-ticket") BusReply.ok(request.correlationId, ZoneAnswer.TicketOwner(73))
          else BusReply.of(request.correlationId, BusReply.Status.NOT_FOUND)
        else -> BusReply.ok(request.correlationId, ZoneAnswer.MapInfo("w", 256, 0, 9, 1.0, 1.0, 1.0))
      }
    }

    val meta = "/v1/companion/masters/5/map/meta"
    assertEquals(401, rest.exchange(meta, HttpMethod.GET, ticketed("metrics-dead-ticket"), String::class.java).statusCode.value())
    assertEquals(200, rest.exchange(meta, HttpMethod.GET, ticketed("metrics-live-ticket"), String::class.java).statusCode.value())
    assertEquals(200, rest.exchange(meta, HttpMethod.GET, ticketed("metrics-live-ticket"), String::class.java).statusCode.value())

    assertEquals(miss + 1, value("api.auth.ticket.lookups", "outcome", "zone_miss"))
    assertEquals(hit + 1, value("api.auth.ticket.lookups", "outcome", "zone_hit"))
    assertEquals(cached + 1, value("api.auth.ticket.lookups", "outcome", "cache_hit"))
    assertEquals(refused + 1, rejected("ticket", "401", "map_meta"))
  }

  @Test
  fun `a repeated question is answered from memory, and the cache says so`() {
    val hits = value("cache.gets", "cache", "answers", "result", "hit")
    val listed = ask("list_masters", "ok")
    answer = { BusReply.ok(it.correlationId, ZoneAnswer.Masters(emptyList())) }

    get("/v1/companion/masters", accountId = 74)
    get("/v1/companion/masters", accountId = 74)

    assertEquals(listed + 1, ask("list_masters", "ok"))
    assertEquals(hits + 1, value("cache.gets", "cache", "answers", "result", "hit"))
  }

  @Test
  fun `a tile is counted as served by the zone, revalidated by the client, or revalidated from the cache`() {
    val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    val fromZone = outcome("map_tile", "served_from_zone")
    val clientCopy = outcome("map_tile", "client_304")
    val revalidated = outcome("map_tile", "cache_revalidated")
    val bytes = value("api.endpoint.body", "route", "map_tile")
    answer = { request ->
      val query = request.query as ZoneQuery.MapTile
      if (query.ifNoneMatch == "\"m1\"") {
        BusReply(request.correlationId, BusReply.Status.NOT_MODIFIED, ZoneAnswer.Tile("\"m1\"", shared = false))
      } else {
        BusReply.ok(request.correlationId, ZoneAnswer.Tile("\"m1\"", shared = false), png)
      }
    }
    val tile = "/v1/companion/masters/5/map/t/4/7/7.png"

    assertEquals(200, rest.exchange(tile, HttpMethod.GET, entity(75), ByteArray::class.java).statusCode.value())
    assertEquals(304, rest.exchange(tile, HttpMethod.GET, entity(75, mapOf(HttpHeaders.IF_NONE_MATCH to "\"m1\"")), ByteArray::class.java).statusCode.value())
    assertEquals(200, rest.exchange(tile, HttpMethod.GET, entity(75), ByteArray::class.java).statusCode.value())

    assertEquals(fromZone + 1, outcome("map_tile", "served_from_zone"))
    assertEquals(clientCopy + 1, outcome("map_tile", "client_304"))
    assertEquals(revalidated + 1, outcome("map_tile", "cache_revalidated"))
    assertEquals(bytes + 2 * png.size, value("api.endpoint.body", "route", "map_tile"))
  }

  @Test
  fun `services and emblems are counted by route`() {
    val png = byteArrayOf(1, 2, 3, 4, 5)
    val hash = "cd".repeat(32)
    val services = outcome("map_services", "served_from_zone")
    val servicesClient = outcome("map_services", "client_304")
    val emblemZone = outcome("emblem", "served_from_zone")
    val emblemMemory = outcome("emblem", "cache_hit")
    val emblemBytes = value("api.endpoint.body", "route", "emblem")
    val emblemHits = value("cache.gets", "cache", "emblems", "result", "hit")
    answer = { request ->
      when (val query = request.query) {
        is ZoneQuery.MapServices ->
          if (query.ifNoneMatch == "\"s2\"") BusReply(request.correlationId, BusReply.Status.NOT_MODIFIED, ZoneAnswer.Services("\"s2\"", emptyList()))
          else BusReply.ok(request.correlationId, ZoneAnswer.Services("\"s2\"", emptyList()))
        is ZoneQuery.GuildEmblem -> BusReply.ok(request.correlationId, ZoneAnswer.Emblem(query.hash), png)
        else -> BusReply.of(request.correlationId, BusReply.Status.NOT_FOUND)
      }
    }

    assertEquals(200, rest.exchange("/v1/companion/masters/5/map/s/2/9/9", HttpMethod.GET, entity(76), String::class.java).statusCode.value())
    assertEquals(304, rest.exchange("/v1/companion/masters/5/map/s/2/9/9", HttpMethod.GET, entity(76, mapOf(HttpHeaders.IF_NONE_MATCH to "\"s2\"")), String::class.java).statusCode.value())
    assertEquals(200, rest.exchange("/v1/companion/emblems/$hash.png", HttpMethod.GET, entity(76), ByteArray::class.java).statusCode.value())
    assertEquals(200, rest.exchange("/v1/companion/emblems/$hash.png", HttpMethod.GET, entity(76), ByteArray::class.java).statusCode.value())

    assertEquals(services + 1, outcome("map_services", "served_from_zone"))
    assertEquals(servicesClient + 1, outcome("map_services", "client_304"))
    assertEquals(emblemZone + 1, outcome("emblem", "served_from_zone"))
    assertEquals(emblemMemory + 1, outcome("emblem", "cache_hit"))
    assertEquals(emblemBytes + 2 * png.size, value("api.endpoint.body", "route", "emblem"))
    assertEquals(emblemHits + 1, value("cache.gets", "cache", "emblems", "result", "hit"))
  }

  @Test
  fun `a socket is a live session until it closes, and the events it hears are counted`() {
    val sockets = value("api.ws.sessions")
    val accounts = value("api.ws.accounts")
    val opened = value("api.ws.auth", "result", "ok")
    val rejectedFrames = value("api.ws.auth", "result", "unauthorized")
    val stance = value("api.fanout.events", "type", "StanceChanged")
    val invalidated = value("api.fanout.invalidations", "target", "answers")
    val replayed = value("api.ws.replayed")

    val frames = CopyOnWriteArrayList<JsonNode>()
    val socket = connect(token(77), lastEventId = null, frames)
    await().atMost(Duration.ofSeconds(5)).until { frames.any { it["type"].asText() == "ready" } }
    val lastSeen = frames.first { it["type"].asText() == "ready" }["lastEventId"].asText()
    assertEquals(sockets + 1, value("api.ws.sessions"))
    assertEquals(accounts + 1, value("api.ws.accounts"))
    assertEquals(opened + 1, value("api.ws.auth", "result", "ok"))

    events.publish(ZoneEvent.StanceChanged(accountId = 77, masterId = 5, playerBestiaId = 7, stance = "HOLD", aggression = 20))
    await().atMost(Duration.ofSeconds(5)).until { frames.any { it["type"].asText() == "event" } }
    assertTrue(value("api.fanout.events", "type", "StanceChanged") >= stance + 1)
    assertTrue(value("api.fanout.invalidations", "target", "answers") >= invalidated + 1)
    socket.close()
    await().atMost(Duration.ofSeconds(5)).untilAsserted { assertEquals(sockets, value("api.ws.sessions")) }

    // Back with the id it saw before the event: the event is replayed.
    val again = CopyOnWriteArrayList<JsonNode>()
    connect(token(77), lastEventId = lastSeen, again).use {
      await().atMost(Duration.ofSeconds(5)).until { again.any { it["type"].asText() == "ready" } }
    }
    assertTrue(value("api.ws.replayed") >= replayed + 1)

    connect("not-a-token", lastEventId = null, CopyOnWriteArrayList()).use {
      await().atMost(Duration.ofSeconds(5)).untilAsserted {
        assertEquals(rejectedFrames + 1, value("api.ws.auth", "result", "unauthorized"))
      }
    }
  }

  @Test
  fun `an event that cannot be read is counted and skipped`() {
    val failures = value("api.bus.stream.failures", "stream", "events", "kind", "decode")
    val delivered = value("api.bus.stream.delivered", "stream", "events")

    zoneClient.connect().use {
      it.sync().xadd(BusKeys.EVENTS, mapOf("account" to "78", "payload" to "{not json"))
    }
    events.publish(ZoneEvent.StanceChanged(accountId = 78, masterId = 5, playerBestiaId = 7, stance = "HOLD", aggression = 1))

    await().atMost(Duration.ofSeconds(5)).untilAsserted {
      assertTrue(value("api.bus.stream.failures", "stream", "events", "kind", "decode") >= failures + 1)
      assertTrue(value("api.bus.stream.delivered", "stream", "events") >= delivered + 1, "the good entry after the bad one still arrives")
    }
  }

  @Test
  fun `a notification is counted as delivered, and what was held back by why`() {
    val delivered = value("api.push.outcomes", "outcome", "delivered")
    subscribe(79)
    events.publish(homeFell(79))
    await().atMost(Duration.ofSeconds(10)).untilAsserted { assertEquals(delivered + 1, value("api.push.outcomes", "outcome", "delivered")) }

    // Somebody with the app open is not pushed.
    val online = value("api.push.dropped", "reason", "online")
    subscribe(80)
    connect(token(80), lastEventId = null, CopyOnWriteArrayList()).use {
      events.publish(homeFell(80))
      await().atMost(Duration.ofSeconds(10)).untilAsserted { assertEquals(online + 1, value("api.push.dropped", "reason", "online")) }
    }

    // Somebody who switched the kind off is not pushed either.
    val unwanted = value("api.push.dropped", "reason", "unwanted")
    subscribe(81)
    val preferences = mapOf("homeFell" to false, "bestiaDied" to true, "constructionDone" to true, "timeZone" to "UTC")
    assertEquals(200, put("/v1/companion/push/preferences", preferences, 81).statusCode.value())
    events.publish(homeFell(81))
    await().atMost(Duration.ofSeconds(10)).untilAsserted { assertEquals(unwanted + 1, value("api.push.dropped", "reason", "unwanted")) }

    // Past the hourly cap the rest is dropped: `push.per-account-per-hour` is 6.
    val capped = value("api.push.dropped", "reason", "rate_limit")
    val before = value("api.push.outcomes", "outcome", "delivered")
    subscribe(82)
    repeat(8) { events.publish(homeFell(82)) }
    await().atMost(Duration.ofSeconds(15)).untilAsserted { assertEquals(capped + 2, value("api.push.dropped", "reason", "rate_limit")) }
    assertEquals(before + 6, value("api.push.outcomes", "outcome", "delivered"))
    assertEquals(6, push.sent.count { it.first == 82L })
  }

  @Test
  fun `requests are timed by route template, and the actuator is not on the app port`() {
    get("/v1/companion/masters", accountId = 83)

    assertTrue(registry.find("http.server.requests").tag("uri", "/v1/companion/masters").timer() != null)
    assertEquals(404, rest.getForEntity("/actuator/prometheus", String::class.java).statusCode.value())
    assertEquals(404, rest.getForEntity("/actuator/health", String::class.java).statusCode.value())
  }

  private fun value(name: String, vararg tags: String): Double {
    val search = registry.find(name).tags(*tags)
    search.counter()?.let { return it.count() }
    search.functionCounter()?.let { return it.count() }
    search.timer()?.let { return it.count().toDouble() }
    search.gauge()?.let { return it.value() }
    error("No meter $name ${tags.toList()}")
  }

  private fun ask(query: String, result: String): Double = value("api.zone.ask", "query", query, "result", result)

  private fun rejected(scheme: String, status: String, route: String): Double {
    return value("api.auth.rejected", "scheme", scheme, "status", status, "route", route)
  }

  private fun outcome(route: String, outcome: String): Double = value("api.endpoint.outcomes", "route", route, "outcome", outcome)

  private fun homeFell(accountId: Long): ZoneEvent {
    return ZoneEvent.HomeFell(accountId = accountId, masterId = 5, masterName = "Ayla", fallen = "Ashfen", newHome = "Brook")
  }

  private fun subscribe(accountId: Long) {
    val subscription = mapOf(
      "endpoint" to "https://fcm.googleapis.com/fcm/send/${UUID.randomUUID()}",
      "keys" to mapOf("p256dh" to "BPC8EVmzvqSQvPRBDi7FjAQqyAJGZsxH9jKT16aTDbUK79Vg4h1rwiPXhadB5_X6i1fOj0--Na1QGZJdQgNlfko", "auth" to "AAAAAAAAAAAAAAAAAAAAAA"),
    )
    val response = rest.exchange(
      "/v1/companion/push/subscriptions",
      HttpMethod.POST,
      HttpEntity(subscription, headers(token(accountId))),
      String::class.java,
    )
    assertEquals(201, response.statusCode.value())
  }

  private fun connect(token: String, lastEventId: String?, frames: MutableList<JsonNode>): WebSocketSession {
    val handler = object : TextWebSocketHandler() {
      override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        frames.add(mapper.readTree(message.payload))
      }
    }
    val socket = StandardWebSocketClient().execute(handler, "ws://localhost:$port/v1/companion/ws").get(5, TimeUnit.SECONDS)
    socket.sendMessage(TextMessage(mapper.writeValueAsString(mapOf("type" to "auth", "token" to token, "lastEventId" to lastEventId))))
    return socket
  }

  private fun get(path: String, accountId: Long = 1, token: String = token(accountId)): ResponseEntity<String> {
    return rest.exchange(path, HttpMethod.GET, HttpEntity<Void>(headers(token)), String::class.java)
  }

  private fun put(path: String, body: Any, accountId: Long): ResponseEntity<String> {
    return rest.exchange(path, HttpMethod.PUT, HttpEntity(body, headers(token(accountId))), String::class.java)
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

  /** Minted as login-server mints it; [audience] and [ttlMillis] bend it into a token that must be refused. */
  private fun token(accountId: Long, audience: String = "companion", ttlMillis: Long = 600_000): String {
    val key = Keys.hmacShaKeyFor("api-test-companion-secret-long-enough-for-hmac".toByteArray(StandardCharsets.UTF_8))
    return Jwts.builder()
      .subject(accountId.toString())
      .issuer("login")
      .audience().add(audience).and()
      .expiration(Date(System.currentTimeMillis() + ttlMillis))
      .signWith(key)
      .compact()
  }
}
