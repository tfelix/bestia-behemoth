package net.bestia.zone.questgen

import net.bestia.zone.questgen.offer.QuestDirector
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Plans the quests of a world when it has none yet: at world creation, and after a build that knows new quest
 * shapes. A seed that has a plan is left alone, so a restart changes nothing.
 *
 * After the catalogues are loaded (106), which the plans read for the items they name. Before the text job (109),
 * which writes the texts of the plans, and so before the entities are loaded (110).
 */
@Component
@Order(108)
class QuestPlanningBootRunner(private val director: QuestDirector) : CommandLineRunner {

  override fun run(vararg args: String?) {
    // The director plans what is new and then offers, so a new world has quests on offer before its first round.
    director.run()
  }
}
