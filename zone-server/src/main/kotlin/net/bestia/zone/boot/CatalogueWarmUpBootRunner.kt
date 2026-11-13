package net.bestia.zone.boot

import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.battle.attack.PoisonedBlade
import net.bestia.zone.battle.attack.TripleBlow
import net.bestia.zone.battle.status.scripts.Songcraft
import net.bestia.zone.casting.plagiarism.Plagiarism
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.fishing.FishingCatalogue
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.spawn.ecs.MobLootSpawner
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads the in-memory catalogues the tick reads, after the importers wrote them (`@Order(100..105)`)
 * and before the tick starts, so their first lookup never reaches the database on the tick.
 */
@Component
@Order(106)
class CatalogueWarmUpBootRunner(
  private val items: ItemTemplateRegistry,
  private val commodities: CommodityItems,
  private val loot: MobLootSpawner,
  private val bestias: BestiaCatalogue,
  private val skills: SkillExecutionService,
  private val fishing: FishingCatalogue,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    items.warmUp()
    commodities.warmUp()
    loot.warmUp()
    bestias.warmUp()
    skills.warmUp()
    fishing.warmUp()
  }
}
