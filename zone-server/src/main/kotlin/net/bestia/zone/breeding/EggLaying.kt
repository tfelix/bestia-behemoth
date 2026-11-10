package net.bestia.zone.breeding

import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.breeding.persistence.BestiaEgg
import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.world.time.Season
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

/**
 * Rolls a farm's clutch and writes the egg: an item instance in no container yet, and the egg row keyed by it. Off
 * the tick, since it reads both parents' rows.
 */
@Service
class EggLaying(
  private val playerBestias: PlayerBestiaRepository,
  private val catalogue: BestiaCatalogue,
  private val items: ItemRepository,
  private val instances: ItemInstanceRepository,
  private val eggs: BestiaEggRepository,
) {

  @Transactional
  fun lay(farm: BreedingFarm, season: Season, random: Random = Random.Default): EggSummary {
    val pair = checkNotNull(farm.pair) { "Farm ${farm.structureId} lays without a pair" }
    val mother = playerBestias.findByIdOrThrow(pair.motherBestiaId)
    val father = playerBestias.findByIdOrThrow(pair.fatherBestiaId)

    val clutch = ClutchRoll(farm.dominantIvs, farm.beastfriendLevel, random).roll(
      mother = parentOf(mother, pair.motherLevel),
      father = parentOf(father, pair.fatherLevel),
      offspringLearnset = learnsetOf(mother.bestia.id),
      season = season,
    )

    val eggItem = checkNotNull(items.findByIdentifier(BreedingFarmService.BESTIA_EGG)) { "items.yml has no bestia_egg" }
    val instance = instances.save(ItemInstance(item = eggItem))
    val egg = eggs.save(BestiaEgg.of(instance.id, clutch))

    return EggSummary.of(egg)
  }

  private fun parentOf(bestia: PlayerBestia, level: Int): ClutchParent {
    val learned = bestia.attackLearnLevels().filterValues { it <= level }
    return ClutchParent(bestia.bestia.id, level, bestia.individualValues, learned)
  }

  private fun learnsetOf(speciesId: Long): Map<Long, Int> {
    return catalogue.learnset(speciesId).associate { it.skillId to it.requiredLevel }
  }
}
