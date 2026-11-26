package net.bestia.api.bugreport

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BugReportIssueTest {

  private val links = BugReportIssue.Files("https://github.com", "bestia/reports", "abc123", "reports/2026/11/7")

  @Test
  fun `the title is the text's first line, shortened`() {
    val long = "x".repeat(100)

    assertEquals("The wolf walks through walls", issueOf(report(text = "\n The wolf walks through walls \nmore")).title)
    assertEquals(80, issueOf(report(text = long)).title.length)
    assertEquals("Crash: NullReferenceException", issueOf(report(text = "NullReferenceException\n  at Foo", kind = BugReport.Kind.CRASH)).title)
  }

  @Test
  fun `labels name the kind and the client version`() {
    assertEquals(listOf("player-report", "client 0.0.1"), issueOf(report()).labels)
    assertEquals(listOf("crash"), issueOf(report(kind = BugReport.Kind.CRASH, clientVersion = null)).labels)
  }

  @Test
  fun `the body embeds the screenshot, links the other files and shows the context's facts`() {
    val files = listOf(
      BugReportFile(7, "screenshot.jpg", byteArrayOf(1)),
      BugReportFile(7, "context.json", """{"os":"Linux","gpu":{"name":"RTX | 4070"},"tags":[1,2]}""".toByteArray()),
      BugReportFile(7, "client.log", byteArrayOf(2)),
    )

    val body = BugReportIssue.of(report(), files, links).body

    assertTrue("![screenshot](https://github.com/bestia/reports/blob/abc123/reports/2026/11/7/screenshot.jpg?raw=true)" in body)
    assertTrue("- [client.log](https://github.com/bestia/reports/blob/abc123/reports/2026/11/7/client.log): tail of the client's Godot log" in body)
    assertTrue("| `os` | Linux |" in body)
    assertTrue("| `gpu.name` | RTX \\| 4070 |" in body)
    assertFalse("tags" in body)
  }

  @Test
  fun `the player's text cannot break out of its fence`() {
    val body = issueOf(report(text = "look ```` here")).body

    assertTrue("`````text\nlook ```` here\n`````" in body)
  }

  @Test
  fun `a crash's occurrences row is kept current, unless someone removed it`() {
    val first = BugReportIssue.Occurrences(1, LocalDateTime.of(2026, 11, 26, 20, 15), "bclient/0.0.1")
    val third = BugReportIssue.Occurrences(3, LocalDateTime.of(2026, 11, 27, 9, 0), "bclient/0.0.2")
    val body = BugReportIssue.of(report(kind = BugReport.Kind.CRASH), emptyList(), null, first).body

    assertTrue("| Occurrences | 1, last seen 2026-11-26 20:15 in bclient/0.0.1 |" in body)
    val updated = BugReportIssue.withOccurrences(body, third)
    assertEquals(body.replace("1, last seen 2026-11-26 20:15 in bclient/0.0.1", "3, last seen 2026-11-27 09:00 in bclient/0.0.2"), updated)
    assertNull(BugReportIssue.withOccurrences("edited by hand", third))
  }

  private fun issueOf(report: BugReport): BugReportIssue {
    return BugReportIssue.of(report, emptyList(), null)
  }

  private fun report(
    text: String = "The wolf walks through walls",
    kind: BugReport.Kind = BugReport.Kind.MANUAL,
    clientVersion: String? = "bclient/0.0.1",
  ): BugReport {
    return BugReport(42, 77, kind, null, clientVersion, text, BugReport.Status.PENDING, LocalDateTime.of(2026, 11, 26, 20, 15))
  }
}
