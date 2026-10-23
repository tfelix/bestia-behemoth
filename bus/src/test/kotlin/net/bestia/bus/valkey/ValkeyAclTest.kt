package net.bestia.bus.valkey

import io.lettuce.core.Range
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.RedisURI
import net.bestia.bus.BusKeys
import net.bestia.bus.StreamEntry
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.KickOrder
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneEvent
import net.bestia.bus.contract.ZoneQuery
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/** The ACL file the dev Valkey runs with lets each service do exactly its part of the bus. */
@Testcontainers
class ValkeyAclTest {

  @Test
  fun `the zone and the api-server users can each play their part`() {
    val zone = connect("zone", "zone-dev-password")
    val api = connect("api", "api-dev-password")

    val directory = ValkeyDirectory(zone)
    directory.register(listOf(7L), "acl-node", ttlSeconds = 30)
    val server = ValkeyRequestServer(zone, "acl-node", { BusReply.ok(it.correlationId, ZoneAnswer.Stance(1, "HOLD", 5)) })
    val publisher = ValkeyEventPublisher(zone)

    val bus = ValkeyNodeBus(api, ValkeyDirectory(api), defaultNode = "acl-node", instanceId = "acl-api")
    val reader = ValkeyStreamReader(api, BusKeys.EVENTS, ZoneEvent::class.java)
    val consumed = CopyOnWriteArrayList<StreamEntry<ZoneEvent>>()
    reader.consume("acl-group", "c1") { consumed += it }

    val write = ZoneQuery.SetStance(masterId = 1, playerBestiaId = 1, stance = "HOLD", aggression = 5)
    assertEquals(BusReply.Status.OK, bus.ask(7L, write, idempotencyKey = "k").get(5, TimeUnit.SECONDS).status)

    Thread.sleep(500)
    publisher.publish(ZoneEvent.Presence(accountId = 7L, masterId = 1, inWorld = true))
    await().atMost(Duration.ofSeconds(10)).until { consumed.size == 1 }

    listOf(reader, bus, publisher, server, directory).forEach { it.close() }
    zone.shutdown()
    api.shutdown()
  }

  @Test
  fun `the login user can append a kick for the zone, and do nothing else`() {
    val login = connect("login", "login-dev-password")
    val zone = connect("zone", "zone-dev-password")

    val kicks = ValkeyStreamReader(zone, BusKeys.KICKS, KickOrder::class.java)
    val received = CopyOnWriteArrayList<StreamEntry<KickOrder>>()
    kicks.consume(BusKeys.kickGroup("acl-node"), "acl-node") { received += it }
    Thread.sleep(500)

    val appender = ValkeyStreamAppender<KickOrder>(login, BusKeys.KICKS, { it.accountId })
    val order = KickOrder(accountId = 7L, reason = "BANNED", kickedAtEpochMs = 1_000L)
    appender.append(order)
    await().atMost(Duration.ofSeconds(10)).until { received.size == 1 }
    assertEquals(order, received.single().payload)

    login.connect().use { connection ->
      assertThrows<RedisCommandExecutionException> { connection.sync().xrange(BusKeys.KICKS, Range.unbounded()) }
      assertThrows<RedisCommandExecutionException> { connection.sync().xadd(BusKeys.EVENTS, mapOf("a" to "b")) }
    }

    listOf(kicks, appender).forEach { it.close() }
    login.shutdown()
    zone.shutdown()
  }

  private fun connect(user: String, password: String): RedisClient {
    val uri = RedisURI.Builder.redis(valkey.host, valkey.getMappedPort(6379))
      .withAuthentication(user, password.toCharArray())
      .build()
    return RedisClient.create(uri)
  }

  companion object {
    private val aclFile = Path.of("../zone-server/valkey/users.acl").toAbsolutePath()

    @Container
    @JvmStatic
    val valkey: GenericContainer<*> = GenericContainer("valkey/valkey:8.1")
      .withCopyFileToContainer(MountableFile.forHostPath(aclFile), "/etc/valkey/users.acl")
      .withCommand("valkey-server", "--aclfile", "/etc/valkey/users.acl")
      .withExposedPorts(6379)
  }
}
