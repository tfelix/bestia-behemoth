package net.bestia.zone.bestia.diary

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How much of a bestia's diary is kept.
 *
 * @property keepDays how long a line is kept.
 * @property maxLinesPerBestia the newest lines kept per bestia; older ones go first, whatever their age.
 */
@ConfigurationProperties(prefix = "bestia.diary")
data class DiaryConfig(
  val keepDays: Long = 7,
  val maxLinesPerBestia: Int = 200,
)
