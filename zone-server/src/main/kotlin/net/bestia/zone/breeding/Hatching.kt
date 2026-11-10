package net.bestia.zone.breeding

import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.LooseInstanceDisposal
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Turns a ready egg into its owner's new bestia at Lv. 1, off the tick. One transaction: the egg is gone exactly when
 * the bestia exists, so a master without a free slot keeps the egg.
 */
@Service
class Hatching(
  private val eggs: BestiaEggRepository,
  private val catalogue: BestiaCatalogue,
  private val disposal: LooseInstanceDisposal,
  private val createOperation: PlayerBestiaCreateOperation,
) {

  /** @throws net.bestia.zone.account.OwnedBestiaPolicyViolationException when the master has no free slot */
  @Transactional
  fun hatch(masterId: Long, eggInstanceId: Long, at: Vec3L) {
    val egg = checkNotNull(eggs.findByIdOrNull(eggInstanceId)) { "Egg $eggInstanceId has no row" }
    val species = catalogue.byId(egg.speciesId)
    val data = PlayerBestiaCreateOperation.PlayerBestiaCreateData(
      bestiaIdentifier = species.identifier,
      spawnPosition = at,
      level = 1,
      sex = egg.sex,
      individualValues = egg.individualValues,
      inheritedAttacks = egg.attackLearnLevels,
    )

    disposal.destroy(eggInstanceId)
    createOperation.createAndSpawn(masterId, data)
  }
}
