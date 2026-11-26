package net.bestia.api.bugreport

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bug-reports")
data class BugReportProperties(
  /** Reports one account may send per hour; null for no limit. */
  val perHour: Int? = null,
  /** Reports one account may send per day; null for no limit. */
  val perDay: Int? = null,
)
