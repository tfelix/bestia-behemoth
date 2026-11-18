package net.bestia.zone.questgen.seed

import net.bestia.worldgen.core.ThreadKind
import net.bestia.zone.chronicle.LivingChronicle
import org.springframework.stereotype.Component

/** The stories the history left open, and the ones play opened since. Only lost artifacts become quests today. */
@Component
class ThreadSeeds(private val chronicle: LivingChronicle) : QuestSeedSource {

  override fun seeds(): List<QuestSeed> {
    return chronicle.openThreads()
      .filter { it.kind == ThreadKind.ARTIFACT_LOST }
      .map { QuestSeed(QuestSeed.Kind.LOST_RELIC, it.actors.first(), it.actors.drop(1), QuestSeed.Uniqueness.UNIQUE) }
  }
}
