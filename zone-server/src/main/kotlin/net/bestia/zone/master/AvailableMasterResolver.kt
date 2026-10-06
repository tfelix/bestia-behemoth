package net.bestia.zone.master

import net.bestia.zone.account.persistence.Account
import net.bestia.zone.account.persistence.AccountRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.MasterSpawnPointService
import org.springframework.stereotype.Component

@Component
class AvailableMasterResolver(
  private val accountRepository: AccountRepository,
  private val world: WorldView,
  private val bestiaInfoFactory: BestiaInfoFactory,
  private val masterSpawnPointService: MasterSpawnPointService
) {

  fun getAvailableMaster(accountId: AccountId): AvailableMasterSMSG {
    val account = accountRepository.findByIdOrThrow(accountId)

    val masterInfos = account.master.map { master ->
      // From the world: this runs before a master is selected, when the session knows no bestias.
      val masterBestiaEntities = world.read { OwnedBestia.ownedBy(this, master.id) }
      val masterBestiaInfos = bestiaInfoFactory.getBestiaInfo(masterBestiaEntities)

      AvailableMasterSMSG.MasterInfo(
        id = master.id,
        name = master.name,
        level = master.level,
        hairColor = master.hairColor,
        skinColor = master.skinColor,
        hair = master.hair,
        face = master.face,
        body = master.body,
        position = master.currentPosition,
        bestias = masterBestiaInfos
      )
    }

    val maxMasterSlots = Account.DEFAULT_MASTER_SLOT_COUNT + account.additionalMasterSlots
    val maxBestiaSlots = Account.DEFAULT_BESTIA_SLOT_COUNT + account.additionalBestiaSlots

    val spawnPoints = masterSpawnPointService.ensureComputed().map {
      AvailableMasterSMSG.SpawnPointCandidate(id = it.id.toInt(), settlementName = it.settlementName, tier = it.tier)
    }

    return AvailableMasterSMSG(
      master = masterInfos,
      maxAvailableMasterSlots = maxMasterSlots,
      maxAvailableBestiaSlots = maxBestiaSlots,
      spawnPoints = spawnPoints
    )
  }
}
