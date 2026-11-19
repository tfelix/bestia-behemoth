package net.bestia.zone.questgen.ending

import net.bestia.worldgen.core.ActorType
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.questgen.plan.QuestPlan
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.townsfolk.rumour.RumourRegistry
import net.bestia.zone.townsfolk.rumour.RumourService
import org.springframework.stereotype.Component

/** News a master carried is really heard where it was told: from then on that town talks of it too. */
@Component
class NewsArrivals(
  private val world: WorldView,
  private val registry: RumourRegistry,
  private val rumours: RumourService,
) {

  fun arrive(plan: QuestPlan) {
    val seed = plan.seed.takeIf { it.kind == QuestSeed.Kind.NEWS } ?: return
    val from = seed.others.firstOrNull { it.type == ActorType.SETTLEMENT }?.index ?: return

    world.post {
      // Gone when the town it came from forgot it on the way. Then it is too old to be news anywhere.
      registry.heardBy(from).firstOrNull { it.id == seed.news?.rumourId }?.let { rumours.retell(it, seed.subject.index) }
    }
  }
}
