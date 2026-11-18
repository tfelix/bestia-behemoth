package net.bestia.zone.questgen.plan

import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.text.TextArg

/** What the planner asks of the world. A port, so the planner is tested with a world made by hand. */
interface QuestWorld {

  /** The real things [slot] can mean for [seed], best first. Empty when this world has none. */
  fun bindings(slot: BindSlot, seed: QuestSeed): List<Binding>

  /** Names of the seed itself for the texts of the quest, such as the artifact's name. */
  fun argsOf(seed: QuestSeed): Map<String, TextArg>

  /** The level a master should have for the quest's payoff. */
  fun levelOf(seed: QuestSeed): Int
}
