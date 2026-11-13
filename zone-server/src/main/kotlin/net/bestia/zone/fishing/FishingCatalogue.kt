package net.bestia.zone.fishing

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.zone.item.ecs.ItemTemplateRegistry
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
      CatchTable.Entry(itemId, level, catch.weight)
    }

    CatchTable(entries, file.overCapHalvingLevels)
  }

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
    fishingSkillId
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
  )

  private data class Rod(val item: String, val maxCatchLevel: Int)

  private data class Catch(val item: String, val weight: Int, val level: Int? = null)

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val RESOURCE = "fishing.yml"
  }
}
