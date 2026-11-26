package net.bestia.api.bugreport

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.Base64

/**
 * GitHub's REST API. Issues cannot carry uploaded files, so a report's files are committed to the repo and the issue
 * links to them. The Git Data API puts all of them in one commit.
 */
@Component
class GitHubReportsClient(properties: BugReportProperties, builder: RestClient.Builder) : GitHubReports {

  private val github = properties.github
  private val repoPath = "/repos/${github.repo}"

  private val rest = builder
    .baseUrl(github.apiUrl)
    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${github.token}")
    .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
    .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
    .build()

  override fun commit(message: String, files: Map<String, ByteArray>): String {
    val branchRef = "$repoPath/git/refs/heads/${github.branch}"
    val head = get("$repoPath/git/ref/heads/${github.branch}")["object"]["sha"].asText()
    val baseTree = get("$repoPath/git/commits/$head")["tree"]["sha"].asText()

    val entries = files.map { (path, content) ->
      mapOf("path" to path, "mode" to REGULAR_FILE, "type" to "blob", "sha" to blob(content))
    }
    val tree = post("$repoPath/git/trees", mapOf("base_tree" to baseTree, "tree" to entries))["sha"].asText()
    val commit = post("$repoPath/git/commits", mapOf("message" to message, "tree" to tree, "parents" to listOf(head)))["sha"].asText()

    // Not forced: if the branch moved meanwhile this fails, and the report is tried again on the new head.
    rest.patch().uri(branchRef).body(mapOf("sha" to commit)).retrieve().toBodilessEntity()

    return commit
  }

  override fun openIssue(title: String, body: String, labels: List<String>): Int {
    val issue = post("$repoPath/issues", mapOf("title" to title, "body" to body, "labels" to labels))

    return issue["number"].asInt()
  }

  private fun blob(content: ByteArray): String {
    val encoded = Base64.getEncoder().encodeToString(content)

    return post("$repoPath/git/blobs", mapOf("content" to encoded, "encoding" to "base64"))["sha"].asText()
  }

  private fun get(path: String): JsonNode {
    return rest.get().uri(path).retrieve().body(JsonNode::class.java)!!
  }

  private fun post(path: String, body: Any): JsonNode {
    return rest.post().uri(path).body(body).retrieve().body(JsonNode::class.java)!!
  }

  private companion object {
    const val REGULAR_FILE = "100644"
  }
}
