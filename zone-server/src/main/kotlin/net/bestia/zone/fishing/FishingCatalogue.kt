package net.bestia.zone.fishing

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.navigation.region.WaterKind
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillIds
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/**
 * `fishing.yml`, with its item names resolved to ids.
 *
 * The file is read at construction; the names are resolved in [warmUp], after the importers wrote the item and
 * skill tables, so a typo fails the boot instead of the first cast.
 */
@Service
class FishingCatalogue(
  private val items: ItemTemplateRegistry,
  private val skillIds: SkillIds,
) {

  private lateinit var file: FishingFile

  val rules: FishingRules by lazy {
    FishingRules(
      reachMetres = file.reachMetres,
      minWaitSeconds = file.minWaitSeconds,
      maxWaitSeconds = file.maxWaitSeconds,
      strikeWindowSeconds = file.strikeWindowSeconds,
    )
  }

  private val maxCatchLevelByRod: Map<Long, Int> by lazy {
    file.rods.associate { itemIdOf(it.item) to it.maxCatchLevel }
  }

  val table: CatchTable by lazy {
    val entries = file.catches.map { catch ->
      val itemId = itemIdOf(catch.item)
      val level = catch.level ?: requireNotNull(items.baseLevelOf(itemId)) { "${catch.item} has no level" }
      CatchTable.Entry(
        itemId = itemId,
        level = level,
        weight = catch.weight,
        waters = catch.waters,
        biomes = catch.biomes,
        corruptedOnly = catch.corrupted,
        weather = catch.weather,
        boostWeather = catch.weatherBoost?.kinds.orEmpty(),
        weatherBoost = catch.weatherBoost?.factor ?: 1.0,
        night = catch.time?.let { it == TimeOfDay.NIGHT },
        nightBoost = catch.nightBoost,
        tags = catch.tags,
      )
    }

    CatchTable(entries, file.overCapHalvingLevels)
  }

  private val baitsByItem: Map<Long, Bait> by lazy {
    file.baits.associate { bait ->
      val itemId = itemIdOf(bait.item)
      itemId to Bait(itemId, bait.favours.associateWith { bait.factor }, bait.waitCutPercent)
    }
  }

  /** What a bait does to a line: the tags it draws, and how much shorter it makes the wait. */
  data class Bait(val itemId: Long, val favoured: Map<CatchTag, Double>, val waitCutPercent: Int)

  /** The bait items, for the quiver to be asked about. */
  val baitItemIds: Set<Long> get() = baitsByItem.keys

  fun baitOf(itemId: Long): Bait? = baitsByItem[itemId]

  /** What a summoned water bestia near the angler does. */
  val helper: Helper get() = file.helper

  /** The chance a strike lands a sunken strongbox instead of the table's draw. */
  val sunkenChestChance: Double get() = file.sunkenChest.chance

  /** How many levels above the country's band the strongbox's gear may be. */
  val sunkenChestLevelsAbove: Int get() = file.sunkenChest.levelsAbove

  /** The highest gear level a strongbox holds: nothing of the legendary tier. */
  val sunkenChestMaxLevel: Int get() = file.sunkenChest.maxLevel

  /** Null when the catalogue has no Fishing skill, which leaves every master untrained. */
  val fishingSkillId: Long? get() = skillIds[SkillId.M_FISHING]

  @PostConstruct
  fun load() {
    val mapper = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
      .build()

    file = ClassPathResource(RESOURCE).inputStream.use { mapper.readValue(it, FishingFile::class.java) }
    rules

    require(file.rods.isNotEmpty()) { "$RESOURCE names no rod" }
    require(file.catches.isNotEmpty()) { "$RESOURCE names no catch" }
    LOG.info { "Loaded ${file.rods.size} fishing rods and ${file.catches.size} catches" }
  }

  fun warmUp() {
    maxCatchLevelByRod.size
    table
    baitsByItem.size
  }

  /** Null for an item that is not a fishing rod. */
  fun maxCatchLevelOf(rodItemId: Long): Int? {
    return maxCatchLevelByRod[rodItemId]
  }

  fun catchLevelCap(fishingRank: Int): Int {
    if (fishingRank <= 0) {
      return file.untrainedCatchLevelCap
    }

    return fishingRank * file.catchLevelCapPerRank
  }

  private fun itemIdOf(name: String): Long {
    return requireNotNull(items.idOf(name)) { "$RESOURCE names $name, which items.yml does not have" }
  }

  private data class FishingFile(
    val untrainedCatchLevelCap: Int,
    val catchLevelCapPerRank: Int,
    val overCapHalvingLevels: Int,
    val reachMetres: Long,
    val minWaitSeconds: Float,
    val maxWaitSeconds: Float,
    val strikeWindowSeconds: Float,
    val rods: List<Rod> = emptyList(),
    val catches: List<Catch> = emptyList(),
    val baits: List<BaitEntry> = emptyList(),
    val helper: Helper = Helper(),
    val sunkenChest: SunkenChest = SunkenChest(),
  )

  private data class Rod(val item: String, val maxCatchLevel: Int)

  private data class Catch(
    val item: String,
    val weight: Int,
    val level: Int? = null,
    val waters: Set<WaterKind> = emptySet(),
    val biomes: Set<Biome> = emptySet(),
    val corrupted: Boolean = false,
    val weather: Set<WeatherKind> = emptySet(),
    val weatherBoost: WeatherBoost? = null,
    val time: TimeOfDay? = null,
    val nightBoost: Double = 1.0,
    val tags: Set<CatchTag> = emptySet(),
  )

  private data class WeatherBoost(val kinds: Set<WeatherKind>, val factor: Double)

  private enum class TimeOfDay { DAY, NIGHT }

  private data class BaitEntry(
    val item: String,
    val favours: Set<CatchTag> = emptySet(),
    val factor: Double = 1.0,
    val waitCutPercent: Int = 0,
  )

  /**
   * A summoned bestia of the water kind standing near the angler.
   *
   * @property rangeMetres how near it must stand
   * @property waitCutPercent how much shorter it makes the wait for a bite
   * @property treasureFactor how much likelier it makes a catch tagged TREASURE
   */
  data class Helper(val rangeMetres: Long = 15, val waitCutPercent: Int = 0, val treasureFactor: Double = 1.0)

  private data class SunkenChest(val chance: Double = 0.0, val levelsAbove: Int = 20, val maxLevel: Int = 80)

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val RESOURCE = "fishing.yml"
  }
}
