package net.bestia.api.bugreport

import net.bestia.api.ApiScenarioTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BugReportAdminScenarioTest : ApiScenarioTestBase() {

  @Autowired
  private lateinit var rest: TestRestTemplate

  @Autowired
  private lateinit var reports: BugReportRepository

  @Autowired
  private lateinit var files: BugReportFileRepository

  @Autowired
  private lateinit var bans: BugReportBanRepository

  @Test
  fun `the admin API needs its token`() {
    assertEquals(401, call(HttpMethod.GET, "/bans", token = null).statusCode.value())
    assertEquals(401, call(HttpMethod.GET, "/bans", token = "wrong").statusCode.value())
    assertEquals(200, call(HttpMethod.GET, "/bans").statusCode.value())
  }

  @Test
  fun `an account is banned and unbanned by its id`() {
    assertEquals(200, call(HttpMethod.PUT, "/bans/9201", mapOf("reason" to "spam")).statusCode.value())
    assertEquals("spam", bans.findById(9201).get().reason)

    assertEquals(204, call(HttpMethod.DELETE, "/bans/9201").statusCode.value())
    assertFalse(bans.existsById(9201))
  }

  @Test
  fun `a report's id bans its sender and drops the sender's waiting reports`() {
    val waiting = reports.save(BugReport(9202, null, BugReport.Kind.MANUAL, null, null, "spam", BugReport.Status.PENDING))
    files.save(BugReportFile(waiting.id, "client.log", "spam".toByteArray()))

    val response = call(HttpMethod.PUT, "/${waiting.id}/ban-sender")

    assertEquals(200, response.statusCode.value())
    assertTrue("\"accountId\":9202" in response.body!!)
    assertEquals("report ${waiting.id}", bans.findById(9202).get().reason)
    assertEquals(BugReport.Status.SHADOWED, reports.findById(waiting.id).get().status)
    assertTrue(files.findAllByReportId(waiting.id).isEmpty())
    assertEquals(404, call(HttpMethod.PUT, "/999999999/ban-sender").statusCode.value())
  }

  private fun call(method: HttpMethod, path: String, body: Any? = null, token: String? = "test-admin-token"): ResponseEntity<String> {
    val headers = HttpHeaders().apply {
      contentType = MediaType.APPLICATION_JSON
      token?.let { setBearerAuth(it) }
    }

    return rest.exchange("/v1/admin/bug-reports$path", method, HttpEntity(body, headers), String::class.java)
  }
}
