package net.bestia.zone.treasure

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ArtifactKind
import net.bestia.worldgen.core.ArtifactRecord
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.history.Names
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.PlayerItemIds
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ItemTitles
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemIntent
import net.bestia.zone.item.stat.ItemLevel
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemStatRoller
import net.bestia.zone.item.stat.ItemStatSheet
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.treasure.persistence.RelicRecord
import net.bestia.zone.treasure.persistence.RelicRecordRepository
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * The artifacts of the world's chronicle, dug up as named gear. Each copy keeps the name the chronicle gave
 * it, and its forger and forge town as its provenance; its stats roll like the best gear of a splendid cache.
 */
@Service
class Relics(
  private val worldService: WorldService,
  private val records: RelicRecordRepository,
  private val items: ItemTemplateRegistry,
  private val stats: ItemStatCatalog,
  private val roller: ItemStatRoller,
  private val asyncJobExecutor: AsyncJobExecutor,
) : ItemTitles {

  private val artifactByCopy = ConcurrentHashMap<Long, Int>()

  init {
    ItemTitles.installed = this
  }

  fun restoreAll() {
    artifactByCopy.clear()
    records.findAll().forEach { artifactByCopy[it.itemInstanceId] = it.artifact }
    LOG.info { "Restored ${artifactByCopy.size} relics" }
  }

  override fun of(uniqueId: Long): String? {
    val artifact = artifactByCopy[uniqueId] ?: return null
    val record = chronicle().artifacts.getOrNull(artifact) ?: return null
    return Names.artifact(record.nameSeed, cultureOf(record), record.kind, record.forgedAtNameSeed)
      .replaceFirstChar { it.uppercase() }
  }

  /** The relic the chronicle buried as [artifact], ready to be given, or null when there is none. */
  fun unearth(artifact: Int): CreateItemIntent? {
    val record = chronicle().artifacts.getOrNull(artifact) ?: return null
    val itemId = items.idOf(identifierOf(record.kind)) ?: return null

    val copy = PlayerItemIds.next()
    artifactByCopy[copy] = artifact
    asyncJobExecutor.submit(JOB_KEY) {
      records.save(RelicRecord(copy, artifact))
    }

    val sheet = stats.sheetOf(itemId)
    val roll = roller.roll(sheet, rolledCapOf(record, sheet))
    return CreateItemIntent(itemId, 1, roll, provenanceOf(record), freshId = copy)
  }

  fun forget(copies: Collection<Long>) {
    copies.forEach(artifactByCopy::remove)
  }

  /** Shown as "crafted by the forger in the town", which is what it is. */
  private fun provenanceOf(record: ArtifactRecord): ItemProvenance? {
    val chronicle = chronicle()
    val forger = chronicle.figures.getOrNull(record.forgedBy) ?: return null
    val culture = cultureOf(record)
    val town = if (record.forgedAtNameSeed == 0L) "" else Names.place(record.forgedAtNameSeed, culture)
    return ItemProvenance(Names.person(forger.nameSeed, culture, forger.role), town)
  }

  /** The forger's people name the piece, as `townsfolk/knowledge/ChronicleNames` does. */
  private fun cultureOf(record: ArtifactRecord): Int {
    val chronicle = chronicle()
    val civ = chronicle.figures.getOrNull(record.forgedBy)?.civ ?: -1
    return chronicle.civs.getOrNull(civ)?.cultureIndex ?: -1
  }

  private fun chronicle(): Chronicle {
    return worldService.generated.world.chronicle
  }

  /**
   * A relic with a longer history rolls better stats. Item level is the root of base level squared plus stat level
   * squared, so with base level 80 these caps on the stat level land a relic between 80 and 110. The fixed stats
   * count toward the cap; the rolled ones get the rest.
   */
  private fun rolledCapOf(record: ArtifactRecord, sheet: ItemStatSheet): Int {
    val fame = (record.provenance.size - 1).coerceAtLeast(0)
    val statCap = (MIN_STAT_CAP + STAT_CAP_PER_EVENT * fame).coerceAtMost(MAX_STAT_CAP)
    val fixed = ItemLevel.LEVELS_PER_POINT * ItemLevel.pointsOf(sheet.fixedStats())
    return (statCap - fixed).toInt().coerceAtLeast(0)
  }

  private fun identifierOf(kind: ArtifactKind): String {
    return "relic_${kind.name.lowercase()}"
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val JOB_KEY = "treasure-relics"

    const val MIN_STAT_CAP = 45
    const val STAT_CAP_PER_EVENT = 10
    const val MAX_STAT_CAP = 75
  }
}
