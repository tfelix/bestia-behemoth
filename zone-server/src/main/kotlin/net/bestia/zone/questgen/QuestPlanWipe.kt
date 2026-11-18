package net.bestia.zone.questgen

import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** A plan points at the people, graves and caches of one world. */
@Component
@Order(9)
class QuestPlanWipe(private val plans: StoredQuestPlanRepository) : WorldScopedData {

  override fun wipe() {
    plans.deleteAllInBatch()
  }
}
