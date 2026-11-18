package net.bestia.zone.questgen

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "questgen")
data class QuestgenConfig(

  /** Quests a town offers at once whatever its size, so a hamlet has something to tell. */
  val offersPerTown: Int = 1,

  /** One more offer for every this many people of a town. */
  val peoplePerExtraOffer: Int = 400,

  /** How long a quest takes work after its first master accepted it. */
  val questRunsFor: Duration = Duration.ofDays(30),
)
