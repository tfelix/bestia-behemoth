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

  /** Experience for finishing a quest, per suggested level of the quest. */
  val expPerLevel: Int = 100,

  /** What a town thinks more of a master who brings its artifact home, in honor. */
  val returnHonor: Int = 15,

  /** What its home town thinks less of a master who keeps the artifact. */
  val keepHonorLoss: Int = 5,

  /** The share of its treasury a town pays for its artifact, and the most it ever pays. */
  val returnTreasuryShare: Double = 0.02,
  val returnCoinsCap: Long = 5_000,
)
