package net.bestia.zone.quest

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "quest")
data class QuestConfig(

  /** Quests one master may have accepted at once, ended ones included until cleared. */
  val logCapacity: Int = 25,

  /** How long an ended quest stays readable in the logs before it is deleted. */
  val endedKeptFor: Duration = Duration.ofDays(3),
)
