package net.bestia.api.bugreport

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.DefaultResponseCreator
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.Base64
import kotlin.test.assertEquals

class GitHubReportsClientTest {

  private val builder = RestClient.builder()
  private val server = MockRestServiceServer.bindTo(builder).build()
  private val client = GitHubReportsClient(
    BugReportProperties(github = BugReportProperties.GitHub(token = "secret", repo = "bestia/reports")),
    builder,
  )

  @Test
  fun `files go into one commit on top of the branch`() {
    expectJson(HttpMethod.GET, "/git/ref/heads/main", """{"object":{"sha":"head1"}}""")
    expectJson(HttpMethod.GET, "/git/commits/head1", """{"tree":{"sha":"tree0"}}""")
    server.expect(requestTo("$REPO/git/blobs"))
      .andExpect(method(HttpMethod.POST))
      .andExpect(jsonPath("$.content").value(Base64.getEncoder().encodeToString("log".toByteArray())))
      .andRespond(json("""{"sha":"blob1"}"""))
    server.expect(requestTo("$REPO/git/trees"))
      .andExpect(jsonPath("$.base_tree").value("tree0"))
      .andExpect(jsonPath("$.tree[0].path").value("reports/2026/11/7/client.log"))
      .andExpect(jsonPath("$.tree[0].sha").value("blob1"))
      .andRespond(json("""{"sha":"tree1"}"""))
    server.expect(requestTo("$REPO/git/commits"))
      .andExpect(method(HttpMethod.POST))
      .andExpect(jsonPath("$.tree").value("tree1"))
      .andExpect(jsonPath("$.parents[0]").value("head1"))
      .andRespond(json("""{"sha":"commit1"}"""))
    server.expect(requestTo("$REPO/git/refs/heads/main"))
      .andExpect(method(HttpMethod.PATCH))
      .andExpect(content().json("""{"sha":"commit1"}""", true))
      .andRespond(json("{}"))

    val commit = client.commit("Report 7", mapOf("reports/2026/11/7/client.log" to "log".toByteArray()))

    assertEquals("commit1", commit)
    server.verify()
  }

  @Test
  fun `an issue is opened with the token and its labels`() {
    server.expect(requestTo("$REPO/issues"))
      .andExpect(method(HttpMethod.POST))
      .andExpect(header("Authorization", "Bearer secret"))
      .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
      .andExpect(jsonPath("$.title").value("The wolf walks through walls"))
      .andExpect(jsonPath("$.labels[0]").value("player-report"))
      .andRespond(json("""{"number":12}"""))

    val number = client.openIssue("The wolf walks through walls", "body", listOf("player-report"))

    assertEquals(12, number)
    server.verify()
  }

  private fun expectJson(method: HttpMethod, path: String, body: String) {
    server.expect(requestTo("$REPO$path")).andExpect(method(method)).andRespond(json(body))
  }

  private fun json(body: String): DefaultResponseCreator {
    return withSuccess(body, MediaType.APPLICATION_JSON)
  }

  private companion object {
    const val REPO = "https://api.github.com/repos/bestia/reports"
  }
}
