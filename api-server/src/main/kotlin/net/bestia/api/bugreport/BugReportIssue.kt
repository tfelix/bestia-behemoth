package net.bestia.api.bugreport

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.format.DateTimeFormatter

/**
 * The issue a report becomes. The body is a summary for a human or an agent to start from; the whole truth is in the
 * committed files it links.
 */
data class BugReportIssue(val title: String, val body: String, val labels: List<String>) {

  /** Where a report's files live in the reports repo, pinned to the commit that added them. */
  class Files(private val webUrl: String, private val repo: String, private val commit: String, val folder: String) {

    fun pageOf(name: String): String {
      return "$webUrl/$repo/blob/$commit/$folder/$name"
    }

    /** A link the issue can embed as an image; GitHub serves it only to people who can see the repo. */
    fun rawOf(name: String): String {
      return "${pageOf(name)}?raw=true"
    }
  }

  companion object {
    private val JSON = jacksonObjectMapper()
    private val RECEIVED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private const val MAX_TITLE_LENGTH = 80
    private const val MAX_CONTEXT_ROWS = 40

    private val FILE_ROLES = mapOf(
      "context.json" to "client state when the report was sent",
      "server.json" to "the zone's view of the master at that moment",
      "client.log" to "tail of the client's Godot log",
      "netlog.txt" to "recent network traffic per message type",
      "chunks.bin" to "chunk dump around the camera, for the dev harnesses",
    )

    fun of(report: BugReport, files: List<BugReportFile>, links: Files?): BugReportIssue {
      return BugReportIssue(titleOf(report), bodyOf(report, files, links), labelsOf(report))
    }

    private fun titleOf(report: BugReport): String {
      val firstLine = report.text.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
      val short = if (firstLine.length > MAX_TITLE_LENGTH) firstLine.take(MAX_TITLE_LENGTH - 1) + "…" else firstLine

      return when (report.kind) {
        BugReport.Kind.MANUAL -> short
        BugReport.Kind.CRASH -> "Crash: $short"
      }
    }

    private fun labelsOf(report: BugReport): List<String> {
      val kindLabel = when (report.kind) {
        BugReport.Kind.MANUAL -> "player-report"
        BugReport.Kind.CRASH -> "crash"
      }
      val versionLabel = report.clientVersion?.substringAfter('/')?.let { "client $it" }

      return listOfNotNull(kindLabel, versionLabel)
    }

    private fun bodyOf(report: BugReport, files: List<BugReportFile>, links: Files?): String {
      val names = files.map { it.name }
      val screenshot = names.firstOrNull { it.startsWith("screenshot.") }

      return buildString {
        appendLine("| | |")
        appendLine("|---|---|")
        summaryOf(report).forEach { (key, value) -> appendLine("| $key | ${cell(value)} |") }
        files.firstOrNull { it.name == "context.json" }?.let { contextRowsOf(it.content) }
          ?.forEach { (key, value) -> appendLine("| `$key` | ${cell(value)} |") }

        appendLine()
        appendLine(if (report.kind == BugReport.Kind.CRASH) "### Error" else "### What the player wrote")
        appendLine(fenced(report.text.orEmpty()))

        if (links != null && screenshot != null) {
          appendLine()
          appendLine("### Screenshot")
          appendLine("![screenshot](${links.rawOf(screenshot)})")
        }

        val others = names.filter { it != screenshot }
        if (links != null && others.isNotEmpty()) {
          appendLine()
          appendLine("### Files")
          appendLine("All in `${links.folder}/` of this repo.")
          others.forEach { appendLine("- [$it](${links.pageOf(it)})" + (FILE_ROLES[it]?.let { role -> ": $role" } ?: "")) }
        }
      }
    }

    private fun summaryOf(report: BugReport): List<Pair<String, String>> {
      return listOfNotNull(
        "Report" to report.id.toString(),
        "Kind" to report.kind.name.lowercase(),
        report.clientVersion?.let { "Client" to it },
        "Account" to report.accountId.toString(),
        report.masterId?.let { "Master" to it.toString() },
        "Received" to "${report.createdAt.format(RECEIVED)} (server time)",
      )
    }

    /** The context's scalar values as `a.b` rows, so the common facts show without opening the file. */
    private fun contextRowsOf(content: ByteArray): List<Pair<String, String>> {
      val root = runCatching { JSON.readTree(content) }.getOrNull() ?: return emptyList()
      val rows = mutableListOf<Pair<String, String>>()
      flatten("", root, rows)

      return rows.take(MAX_CONTEXT_ROWS)
    }

    private fun flatten(prefix: String, node: JsonNode, rows: MutableList<Pair<String, String>>) {
      when {
        node.isObject -> node.fields().forEach { (name, child) -> flatten(if (prefix.isEmpty()) name else "$prefix.$name", child, rows) }
        node.isValueNode -> rows += prefix to node.asText()
      }
    }

    private fun cell(value: String): String {
      return value.replace("|", "\\|").replace("\n", " ")
    }

    /** A fence longer than any backtick run in [text], so the player's text cannot close it. */
    private fun fenced(text: String): String {
      val longestRun = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
      val fence = "`".repeat(maxOf(3, longestRun + 1))

      return "${fence}text\n$text\n$fence"
    }
  }
}
