package net.bestia.zone.questgen.seed

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.prop.PropRestoration
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.questgen.QuestgenConfig
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Component

/**
 * A town with buildings somebody destroyed. They do not come back by themselves, so the town asks for the
 * material to rebuild them. One seed per town.
 */
@Component
class DamageSeeds(
  private val world: WorldView,
  private val divergence: WorldObjectDivergenceRegistry,
  private val restoration: PropRestoration,
  private val sites: SettlementSiteIndex,
  private val items: ItemTemplateRegistry,
  private val worldService: WorldService,
  private val config: QuestgenConfig,
) : QuestSeedSource {

  override fun seeds(): List<QuestSeed> {
    val material = items.idOf(config.repairItem) ?: return emptyList()
    val chronicle = worldService.generated.world.chronicle
    val standing = chronicle.settlements.filter { chronicle.settlementStood(it.index, chronicle.presentYear) }.map { it.index }

    // The destroyed props are kept on the tick.
    return world.read {
      // Nearly always no building in the world is destroyed, and then no town has to be looked at.
      if (divergence.all().values.none { it.kind.isBuilding }) {
        return@read emptyList()
      }

      standing.mapNotNull { town ->
        val destroyed = destroyedBuildingsOf(town).size
        if (destroyed == 0) {
          return@mapNotNull null
        }
        QuestSeed(
          QuestSeed.Kind.TOWN_DAMAGE,
          Actor(ActorType.SETTLEMENT, town),
          uniqueness = QuestSeed.Uniqueness.SHARED,
          need = QuestSeed.Need(material, destroyed * config.repairUnitsPerBuilding),
        )
      }
    }
  }

  /** The prop ids of the town's buildings that are gone. Tick thread. */
  fun destroyedBuildingsOf(town: Int): List<Long> {
    return sites.siteOf(town)?.buildings.orEmpty().map { it.propId }.filter(restoration::isDestroyed)
  }
}
