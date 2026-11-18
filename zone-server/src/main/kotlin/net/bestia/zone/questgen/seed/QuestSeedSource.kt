package net.bestia.zone.questgen.seed

/** Watches one part of the world and reports the reasons for a quest it sees there now. Off the tick. */
interface QuestSeedSource {

  fun seeds(): List<QuestSeed>
}
