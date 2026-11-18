package net.bestia.zone.master

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.SettlementRecord
import net.bestia.zone.text.TextArg
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.text.TextRef
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * What the master creation screen tells a player about a home on offer.
 *
 * Every word is the client's. A fact is a translation key with typed arguments, rendered like a dialog
 * line, and the biome is a token. The chronicle's own prose cannot be used here: it is English forever,
 * see [net.bestia.zone.world.SettlementLoreService].
 */
@Service
class HomeFacts(
  private val worldService: WorldService,
) {

  fun historyOf(settlementIndex: Int): List<TextRef> {
    if (!worldService.isLoaded) return emptyList()

    val record = worldService.generated.world.chronicle.settlements.getOrNull(settlementIndex)
      ?: return emptyList()

    return historyOf(record)
  }

  fun biomeTokenAt(position: Vec3L): String {
    if (!worldService.isLoaded) return ""

    val voxelSize = worldService.config.voxelSize
    val biome = worldService.generated.materializer.surface.biomeAt(position.x * voxelSize, position.y * voxelSize)

    return biomeToken(biome)
  }

  companion object {
    const val FOUNDED = "HOME_FACT_FOUNDED"
    const val WALLED = "HOME_FACT_WALLED"

    // Two keys rather than one with a count, because Godot's CSV translations have no plural forms.
    const val SACKED_ONCE = "HOME_FACT_SACKED_ONCE"
    const val SACKED_MANY = "HOME_FACT_SACKED_MANY"

    /** Every key [historyOf] can produce, so a test can hold the client's string table to them. */
    val HISTORY_KEYS = listOf(FOUNDED, WALLED, SACKED_ONCE, SACKED_MANY)

    fun historyOf(record: SettlementRecord): List<TextRef> = buildList {
      if (record.wasFounded) {
        add(TextRef(FOUNDED, mapOf("year" to TextArg.Number(record.foundedYear.toLong()))))
      }
      if (record.wallYear != 0) {
        add(TextRef(WALLED, mapOf("year" to TextArg.Number(record.wallYear.toLong()))))
      }
      when (record.timesSacked) {
        0 -> Unit
        1 -> add(TextRef(SACKED_ONCE))
        else -> add(TextRef(SACKED_MANY, mapOf("times" to TextArg.Number(record.timesSacked.toLong()))))
      }
    }

    fun biomeToken(biome: Biome) = "BIOME_${biome.name}"
  }
}
