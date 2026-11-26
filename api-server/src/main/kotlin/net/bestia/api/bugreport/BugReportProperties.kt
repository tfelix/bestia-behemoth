package net.bestia.api.bugreport

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "bug-reports")
data class BugReportProperties(
  /** Reports one account may send per hour; null for no limit. */
  val perHour: Int? = null,
  /** Reports one account may send per day; null for no limit. */
  val perDay: Int? = null,
  /** How often a crash seen again may add a comment with its files to its issue. */
  val crashCommentInterval: Duration = Duration.ofHours(1),
  val github: GitHub = GitHub(),
) {

  /** The private repo reports become issues in. Without a token and a repo, reports wait here unpublished. */
  data class GitHub(
    /** A fine-grained token for [repo] only, with write access to contents and issues. */
    val token: String? = null,
    /** `owner/name`. The repo needs one commit on [branch], because files are committed on top of it. */
    val repo: String? = null,
    val branch: String = "main",
    val apiUrl: String = "https://api.github.com",
    val webUrl: String = "https://github.com",
  ) {

    val isConfigured: Boolean
      get() {
        return !token.isNullOrBlank() && !repo.isNullOrBlank()
      }
  }
}
