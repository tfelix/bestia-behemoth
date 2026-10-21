package net.bestia.bus.valkey

import io.lettuce.core.RedisClient
import net.bestia.bus.Directory
import net.bestia.bus.StreamedEvent
import net.bestia.bus.ZoneUnavailableException
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneEvent
import net.bestia.bus.contract.ZoneQuery
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@Testcontainers
class ValkeyBusTest {

  private val accountId = 42L

  @Test
  fun `a question reaches the zone node and its answer comes back`() {
    val (bus, server) = pair("n1") { request ->
      BusReply.ok(request.correlationId, ZoneAnswer.Masters(emptyList()))
    }

    server.use {
      val reply = bus.ask(accountId, ZoneQuery.ListMasters).get(5, TimeUnit.SECONDS)

      assertEquals(BusReply.Status.OK, reply.status)
      assertEquals(ZoneAnswer.Masters(emptyList()), reply.answer)
    }
    bus.close()
  }

  @Test
  fun `the directory sends an account in the world to its own node`() {
    val directory = ValkeyDirectory(client)
    directory.register(listOf(accountId), "n2", ttlSeconds = 30)

    val server = ValkeyRequestServer(client, "n2", { BusReply.ok(it.correlationId, ZoneAnswer.Masters(emptyList())) })
    val bus = ValkeyNodeBus(client, directory, defaultNode = "nobody-here", instanceId = "i2")

    server.use {
      assertEquals(BusReply.Status.OK, bus.ask(accountId, ZoneQuery.ListMasters).get(5, TimeUnit.SECONDS).status)
    }
    directory.remove(accountId)
    assertNull(directory.nodeOf(accountId))
    bus.close()
    directory.close()
  }

  @Test
  fun `asking a node nobody serves fails at once`() {
    val bus = ValkeyNodeBus(client, emptyDirectory, defaultNode = "ghost", instanceId = "i3")

    val failure = assertThrows<ExecutionException> { bus.ask(accountId, ZoneQuery.ListMasters).get(5, TimeUnit.SECONDS) }

    assertIs<ZoneUnavailableException>(failure.cause)
    bus.close()
  }

  @Test
  fun `a zone that never answers times out`() {
    val release = CountDownLatch(1)
    val (bus, server) = pair("n4") { request ->
      release.await()
      BusReply.ok(request.correlationId, ZoneAnswer.Masters(emptyList()))
    }

    server.use {
      val failure = assertThrows<ExecutionException> {
        bus.ask(accountId, ZoneQuery.ListMasters, timeout = Duration.ofMillis(300)).get(5, TimeUnit.SECONDS)
      }
      assertIs<TimeoutException>(failure.cause)
      release.countDown()
    }
    bus.close()
  }

  @Test
  fun `a request over the in-flight limit is answered busy`() {
    val release = CountDownLatch(1)
    val (bus, server) = pair("n5", maxInFlight = 1) { request ->
      release.await()
      BusReply.ok(request.correlationId, ZoneAnswer.Masters(emptyList()))
    }

    server.use {
      val first = bus.ask(accountId, ZoneQuery.ListMasters)
      await().until { server.inFlightCount == 1 }
      val second = bus.ask(accountId, ZoneQuery.ListMasters).get(5, TimeUnit.SECONDS)

      assertEquals(BusReply.Status.BUSY, second.status)
      release.countDown()
      assertEquals(BusReply.Status.OK, first.get(5, TimeUnit.SECONDS).status)
    }
    bus.close()
  }

  @Test
  fun `a repeated write gets the first reply and does not run again`() {
    val runs = AtomicInteger()
    val (bus, server) = pair("n6") { request ->
      runs.incrementAndGet()
      BusReply.ok(request.correlationId, ZoneAnswer.Stance(11, "HOLD", 20))
    }
    val write = ZoneQuery.SetStance(masterId = 1, playerBestiaId = 11, stance = "HOLD", aggression = 20)

    server.use {
      val first = bus.ask(accountId, write, idempotencyKey = "same").get(5, TimeUnit.SECONDS)
      val second = bus.ask(accountId, write, idempotencyKey = "same").get(5, TimeUnit.SECONDS)

      assertEquals(first.answer, second.answer)
      assertEquals(1, runs.get())
    }
    bus.close()
  }

  @Test
  fun `events reach a live tail, a consumer group and a replay`() {
    val reader = ValkeyEventReader(client)
    val tailed = CopyOnWriteArrayList<StreamedEvent>()
    val consumed = CopyOnWriteArrayList<StreamedEvent>()
    val before = reader.newestId()

    reader.tail("test") { tailed += it }
    reader.consume("test-group", "c1") { consumed += it }
    // Both loops have to be blocked in their read before the event goes out, or the tail would start after it.
    Thread.sleep(500)

    val publisher = ValkeyEventPublisher(client)
    val event = ZoneEvent.ConstructionDone(accountId = accountId, masterId = 5, kind = "WORKBENCH")
    publisher.publish(event)
    publisher.publish(ZoneEvent.ConstructionDone(accountId = 99, masterId = 6, kind = "WORKBENCH"))

    await().atMost(Duration.ofSeconds(10)).until { tailed.size == 2 && consumed.size == 2 }
    assertEquals(event, tailed.first().event)
    assertEquals(listOf(event), reader.since(before, accountId).map { it.event })

    publisher.close()
    reader.close()
  }

  private fun pair(
    node: String,
    maxInFlight: Int = 64,
    handler: (net.bestia.bus.contract.BusRequest) -> BusReply,
  ): Pair<ValkeyNodeBus, ValkeyRequestServer> {
    val server = ValkeyRequestServer(client, node, handler, maxInFlight)
    val bus = ValkeyNodeBus(client, emptyDirectory, defaultNode = node, instanceId = "api-$node")
    return bus to server
  }

  private val emptyDirectory = object : Directory {
    override fun register(accountIds: Collection<Long>, nodeId: String, ttlSeconds: Long) {}

    override fun remove(accountId: Long) {}

    override fun nodeOf(accountId: Long): String? {
      return null
    }
  }

  companion object {
    @Container
    @JvmStatic
    val valkey: GenericContainer<*> = GenericContainer("valkey/valkey:8.1").withExposedPorts(6379)

    private lateinit var client: RedisClient

    @BeforeAll
    @JvmStatic
    fun connect() {
      client = RedisClient.create("redis://${valkey.host}:${valkey.getMappedPort(6379)}")
    }

    @AfterAll
    @JvmStatic
    fun disconnect() {
      client.shutdown()
    }
  }
}
