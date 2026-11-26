package net.bestia.api.bugreport

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import net.bestia.api.ApiScenarioTestBase
import net.bestia.api.ApiTestContainers
import net.bestia.api.RecordingGitHub
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.bus.valkey.ValkeyRequestServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reports sent the way the game client sends them: multipart, with its ticket, which a stand-in zone resolves. */
class BugReportScenarioTest : ApiScenarioTestBase() {

  @Autowired
  private lateinit var rest: TestRestTemplate

  @Autowired
  private lateinit var reports: BugReportRepository

  @Autowired
  private lateinit var files: BugReportFileRepository

  @Autowired
  private lateinit var bans: BugReportBanRepository

  @Autowired
  private lateinit var publisher: BugReportPublisher

  @Autowired
  private lateinit var crashes: BugCrashSignatureRepository

  @Autowired
  private lateinit var github: RecordingGitHub

  private lateinit var zoneClient: RedisClient
  private lateinit var zone: ValkeyRequestServer

  @BeforeAll
  fun startZone() {
    val uri = RedisURI.Builder.redis(ApiTestContainers.valkey.host, ApiTestContainers.valkey.getMappedPort(6379))
      .withAuthentication("zone", "zone-dev-password".toCharArray())
      .build()
    zoneClient = RedisClient.create(uri)
    zone = ValkeyRequestServer(zoneClient, "zone-1", { request ->
      when (val query = request.query) {
        is ZoneQuery.WhoseTicket -> query.ticket.removePrefix("ticket-").toLongOrNull()
          ?.let { BusReply.ok(request.correlationId, ZoneAnswer.TicketOwner(it)) }
          ?: BusReply.of(request.correlationId, BusReply.Status.NOT_FOUND)
        // Account 9111 has no master in the world.
        is ZoneQuery.BugReportSnapshot -> if (request.accountId == 9111L) {
          BusReply.of(request.correlationId, BusReply.Status.NOT_FOUND)
        } else {
          BusReply.ok(request.correlationId, ZoneAnswer.Snapshot("""{"masterId":${request.accountId}}"""))
        }
        else -> BusReply.of(request.correlationId, BusReply.Status.INVALID)
      }
    })
  }

  @AfterAll
  fun stopZone() {
    zone.close()
    zoneClient.shutdown()
  }

  @BeforeEach
  fun githubUp() {
    github.down = false
  }

  @Test
  fun `a report is kept with its files, the log scrubbed`() {
    val response = send(
      accountId = 9101,
      parts = mapOf(
        "text" to "The wolf walks through walls",
        "masterId" to "77",
        "clientVersion" to "bclient/0.0.1",
        "screenshot" to file(JPEG),
        "log" to file("signed in as ayla@example.com".toByteArray()),
      ),
    )

    assertEquals(202, response.statusCode.value())
    val report = reports.findAll().single { it.accountId == 9101L }
    assertEquals(BugReport.Status.PENDING, report.status)
    assertEquals(BugReport.Kind.MANUAL, report.kind)
    assertEquals(77L, report.masterId)
    assertEquals("The wolf walks through walls", report.text)
    val stored = files.findAllByReportId(report.id).associate { it.name to it.content.toString(Charsets.UTF_8) }
    assertEquals(setOf("screenshot.jpg", "client.log", "server.json"), stored.keys)
    assertEquals("signed in as <email>", stored["client.log"])
    assertEquals("""{"masterId":9101}""", stored["server.json"])
  }

  @Test
  fun `a report needs a ticket`() {
    val parts = LinkedMultiValueMap<String, Any>().apply { add("text", "hello") }
    val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }

    val response = rest.postForEntity(PATH, HttpEntity(parts, headers), String::class.java)

    assertEquals(401, response.statusCode.value())
  }

  @Test
  fun `a report without text, a crash without signature and a fake screenshot are refused`() {
    assertEquals(400, send(9102, mapOf("text" to "   ")).statusCode.value())
    assertEquals(400, send(9102, mapOf("text" to "NullReferenceException", "kind" to "crash")).statusCode.value())
    assertEquals(400, send(9102, mapOf("text" to "see image", "screenshot" to file("<svg/>".toByteArray()))).statusCode.value())
    assertTrue(reports.findAll().none { it.accountId == 9102L })
  }

  @Test
  fun `a crash report keeps its signature`() {
    val signature = "a".repeat(64)

    val response = send(9103, mapOf("text" to "NullReferenceException at Foo.Bar", "kind" to "crash", "signature" to signature))

    assertEquals(202, response.statusCode.value())
    val report = reports.findAll().single { it.accountId == 9103L }
    assertEquals(BugReport.Kind.CRASH, report.kind)
    assertEquals(signature, report.signature)
  }

  @Test
  fun `an account over its hourly limit is told to wait`() {
    repeat(3) { assertEquals(202, send(9104, mapOf("text" to "report $it")).statusCode.value()) }

    val response = send(9104, mapOf("text" to "one too many"))

    assertEquals(429, response.statusCode.value())
    assertEquals(3, reports.findAll().count { it.accountId == 9104L })
  }

  @Test
  fun `a shadow-banned account is answered as usual, and nothing of its report is kept`() {
    bans.save(BugReportBan(9105, "spam"))

    val response = send(9105, mapOf("text" to "spam spam spam", "screenshot" to file(JPEG)))

    assertEquals(202, response.statusCode.value())
    val report = reports.findAll().single { it.accountId == 9105L }
    assertEquals(BugReport.Status.SHADOWED, report.status)
    assertNull(report.text)
    assertTrue(files.findAllByReportId(report.id).isEmpty())
  }

  @Test
  fun `a waiting report becomes an issue, its files committed and then dropped here`() {
    send(9106, mapOf("text" to "Trees float", "screenshot" to file(JPEG), "context" to file("""{"os":"Linux"}""".toByteArray())))
    val waiting = reports.findAll().single { it.accountId == 9106L }

    publisher.publishDue()

    val sent = reports.findById(waiting.id).get()
    assertEquals(BugReport.Status.SENT, sent.status)
    val issue = github.issues.single { it.number == sent.issueNumber }
    assertEquals("Trees float", issue.title)
    assertTrue("| `os` | Linux |" in issue.body)
    val folder = "reports/%04d/%02d/%d".format(sent.createdAt.year, sent.createdAt.monthValue, sent.id)
    assertTrue(github.commits.any { it.keys == setOf("$folder/screenshot.jpg", "$folder/context.json", "$folder/server.json") })
    assertTrue(files.findAllByReportId(sent.id).isEmpty())
  }

  @Test
  fun `while GitHub is down a report waits, and is tried again once its backoff is over`() {
    send(9107, mapOf("text" to "Rain falls upwards"))
    val waiting = reports.findAll().single { it.accountId == 9107L }
    github.down = true

    publisher.publishDue()

    val failed = reports.findById(waiting.id).get()
    assertEquals(BugReport.Status.PENDING, failed.status)
    assertEquals(1, failed.attempts)
    assertTrue(assertNotNull(failed.nextAttemptAt).isAfter(LocalDateTime.now()))

    github.down = false
    publisher.publishDue()
    assertEquals(BugReport.Status.PENDING, reports.findById(waiting.id).get().status)

    failed.nextAttemptAt = LocalDateTime.now().minusSeconds(1)
    reports.save(failed)
    publisher.publishDue()
    assertEquals(BugReport.Status.SENT, reports.findById(waiting.id).get().status)
  }

  @Test
  fun `a crash seen again joins its issue, and adds its files only once the comment interval is over`() {
    val crash = mapOf("text" to "NullReferenceException at Foo.Bar", "kind" to "crash", "signature" to "b".repeat(64))
    send(9108, crash + ("log" to file("first".toByteArray())))
    publisher.publishDue()
    val issue = github.issue(reports.findAll().single { it.accountId == 9108L }.issueNumber!!)
    assertEquals("Crash: NullReferenceException at Foo.Bar", issue.title)
    assertTrue("| Occurrences | 1, last seen" in issue.body)

    send(9109, crash + ("log" to file("second".toByteArray())))
    publisher.publishDue()

    assertEquals(issue.number, reports.findAll().single { it.accountId == 9109L }.issueNumber)
    assertTrue("| Occurrences | 2, last seen" in issue.body)
    assertTrue(github.comments.none { it.first == issue.number })

    val known = crashes.findById("b".repeat(64)).get()
    known.lastCommentAt = LocalDateTime.now().minusHours(2)
    crashes.save(known)
    send(9110, crash + ("log" to file("third".toByteArray())))
    publisher.publishDue()

    assertTrue("| Occurrences | 3, last seen" in issue.body)
    val comment = github.comments.single { it.first == issue.number }.second
    assertTrue(comment.startsWith("**Seen again.**"))
    assertTrue("client.log" in comment)
    assertEquals(1, github.issues.count { it.title == issue.title })
  }

  @Test
  fun `a report from an account without a master in the world says why the zone's view is missing`() {
    send(9111, mapOf("text" to "Stuck on the master select screen"))

    val report = reports.findAll().single { it.accountId == 9111L }
    val server = files.findAllByReportId(report.id).single { it.name == "server.json" }.content.toString(Charsets.UTF_8)
    assertEquals("""{"unavailable":"the zone answered NOT_FOUND"}""", server)
  }

  private fun send(accountId: Long, parts: Map<String, Any>): ResponseEntity<String> {
    val body = LinkedMultiValueMap<String, Any>().apply { parts.forEach { (name, value) -> add(name, value) } }
    val headers = HttpHeaders().apply {
      contentType = MediaType.MULTIPART_FORM_DATA
      set(HttpHeaders.AUTHORIZATION, "Ticket ticket-$accountId")
    }

    return rest.postForEntity(PATH, HttpEntity(body, headers), String::class.java)
  }

  /** A part with a file name, so Spring hands it to the controller as a file. */
  private fun file(bytes: ByteArray): ByteArrayResource {
    return object : ByteArrayResource(bytes) {
      override fun getFilename(): String {
        return "upload"
      }
    }
  }

  private companion object {
    const val PATH = "/v1/companion/bug-reports"
    val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
  }
}
