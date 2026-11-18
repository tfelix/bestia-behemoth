package net.bestia.zone.questgen

import net.bestia.zone.questgen.offer.QuestDirector
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Plans the quests of a world when it has none yet: at world creation, and after a build that knows new quest
 * shapes. A seed that has a plan is left alone, so a restart changes nothing. After the towns and the relics.
 */
@Component
@Order(130)
class QuestPlanningBootRunner(
  private val planning: QuestPlanning,
  private val director: QuestDirector,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    planning.planNewSeeds()
    // At once, so a new world has quests on offer before the director's first round.
    director.run()
  }
}
