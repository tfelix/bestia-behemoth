package net.bestia.zone.boot

import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.crafting.MasterCraftBonusService
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.ecs.item.ItemTemplateRegistry
import net.bestia.zone.environment.weather.EnvironmentalExposureSystem
import net.bestia.zone.environment.weather.WeatherPublisher
import net.bestia.zone.item.loot.LootItemEntitySpawner
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads the in-memory catalogues the tick reads, after the importers wrote them (`@Order(100..104)`)
 * and before the tick starts, so their first lookup never reaches the database on the tick.
 */
@Component
@Order(106)
class CatalogueWarmUpBootRunner(
  private val items: ItemTemplateRegistry,
  private val commodities: CommodityItems,
  private val loot: LootItemEntitySpawner,
  private val bestias: BestiaCatalogue,
  private val craftBonus: MasterCraftBonusService,
  private val exposure: EnvironmentalExposureSystem,
  private val weather: WeatherPublisher,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    items.warmUp()
    commodities.warmUp()
    loot.warmUp()
    bestias.all()
    craftBonus.warmUp()
    exposure.warmUp()
    weather.weatherSenseSkillId
  }
}
