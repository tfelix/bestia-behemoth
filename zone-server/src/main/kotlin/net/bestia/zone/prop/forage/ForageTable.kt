package net.bestia.zone.prop.forage

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.worldgen.bio.Biome
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.InputStream
import java.util.Random

/**
 * `forage.yml`: what a forage patch yields in each biome. A pick takes one item from the patch's biome, weighted,
 * and with [secondPickChance] a second; only what the picker's Herbalism reaches is in the draw.
 *
 * The file is read at construction; its item names are resolved in [warmUp], after the importer wrote the item
 * table, so a typo fails the boot rather than the first pick.
 */
@Service
class ForageTable(
  private val items: ItemTemplateRegistry,
) {

  /** One thing a biome's patches grow: [level] is the item's, which Herbalism has to reach. */
  data class Entry(val itemId: Long, val level: Int, val weight: Int)

  private lateinit var file: ForageFile

  val secondPickChance: Double get() = file.secondPickChance

  private val byBiome: Map<Biome, List<Entry>> by lazy {
    file.biomes.mapValues { (biome, entries) ->
      entries.map { dto ->
        val itemId = requireNotNull(items.idOf(dto.item)) { "$RESOURCE names ${dto.item} under $biome, which items.yml does not have" }
        val level = requireNotNull(items.baseLevelOf(itemId)) { "${dto.item} has no level" }
        Entry(itemId, level, dto.weight)
      }
    }
  }

  @PostConstruct
  fun load() {
    ClassPathResource(RESOURCE).inputStream.use { loadFrom(it) }
    LOG.info { "Loaded forage tables for ${file.biomes.size} biomes" }
  }

  internal fun loadFrom(input: InputStream) {
    file = MAPPER.readValue(input, ForageFile::class.java)
    require(file.secondPickChance in 0.0..1.0) { "$RESOURCE: second-pick-chance must be in [0,1]" }
    file.biomes.forEach { (biome, entries) ->
      require(entries.isNotEmpty()) { "$RESOURCE: $biome lists nothing" }
      require(entries.all { it.weight > 0 }) { "$RESOURCE: every weight under $biome must be positive" }
    }
  }

  fun warmUp() {
    byBiome.size
  }

  /** What grows in [biome], or nothing for a biome the file leaves out. */
  fun entriesOf(biome: Biome): List<Entry> = byBiome[biome].orEmpty()

  /**
   * One pick of a patch in [biome] by a picker whose Herbalism reaches [maxLevel]: item id to amount. Empty when
   * nothing there is within reach, which is the picker's to be told rather than a wild herb in its place.
   */
  fun pick(biome: Biome, maxLevel: Int, random: Random): Map<Long, Int> {
    val reachable = entriesOf(biome).filter { it.level <= maxLevel }
    if (reachable.isEmpty()) return emptyMap()

    val picks = if (random.nextDouble() < file.secondPickChance) 2 else 1
    val total = reachable.sumOf { it.weight }

    return buildMap {
      repeat(picks) {
        var roll = random.nextInt(total)
        val entry = reachable.first { candidate -> (roll < candidate.weight).also { roll -= candidate.weight } }
        merge(entry.itemId, 1, Int::plus)
      }
    }
  }

  internal data class ForageFile(
    val secondPickChance: Double,
    val biomes: Map<Biome, List<EntryDto>> = emptyMap(),
  )

  internal data class EntryDto(val item: String, val weight: Int)

  companion object {
    private val LOG = KotlinLogging.logger { }
    const val RESOURCE = "forage.yml"

    private val MAPPER = JsonMapper.builder(YAMLFactory())
      .addModule(kotlinModule())
      .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
      .build()
  }
}
