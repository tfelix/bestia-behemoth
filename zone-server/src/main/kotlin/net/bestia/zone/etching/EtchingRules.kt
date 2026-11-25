package net.bestia.zone.etching

import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.essence.GemTier
import org.springframework.stereotype.Component

/** Which Bestia may fill which cut gem. */
@Component
class EtchingRules(
  private val catalogue: BestiaCatalogue,
  private val itemTemplates: ItemTemplateRegistry,
) {

  fun tierOfGem(gemItemId: Long): GemTier? = itemTemplates.identifierOf(gemItemId)?.let(GemTier::ofCutGem)

  /** Whether a Bestia of [speciesId] at [level] may fill a [tier] gem. A boss fills only the gem of its level. */
  fun fits(speciesId: Long, level: Int, tier: GemTier): Boolean {
    val species = catalogue.find(speciesId) ?: return false
    val bossGem = if (species.boss || species.worldBoss) GemTier.ofBossLevel(species.referenceLevel) else null

    return species.essence != null && level >= tier.minBestiaLevel && (bossGem == null || bossGem == tier)
  }
}
