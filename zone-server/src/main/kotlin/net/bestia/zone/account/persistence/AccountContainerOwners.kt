package net.bestia.zone.account.persistence

import net.bestia.zone.account.MasterNotFoundException
import net.bestia.zone.account.PlayerBestiaNotFoundException
import net.bestia.zone.item.container.ContainerOwner
import net.bestia.zone.item.container.ContainerOwners
import net.bestia.zone.prop.persistence.PlayerStructure
import net.bestia.zone.prop.persistence.PlayerStructureRepository
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Component

@Component
class AccountContainerOwners(
  private val masterRepository: MasterRepository,
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val playerStructureRepository: PlayerStructureRepository,
) : ContainerOwners {

  override fun lockedMaster(masterId: Long): ContainerOwner {
    return masterRepository.findByIdForUpdate(masterId) ?: throw MasterNotFoundException()
  }

  override fun lockedPlayerBestia(playerBestiaId: PlayerBestiaId): ContainerOwner {
    return playerBestiaRepository.findByIdForUpdate(playerBestiaId)
      ?: throw PlayerBestiaNotFoundException(playerBestiaId)
  }

  override fun lockedPlayerBestiaOf(masterId: Long, playerBestiaId: PlayerBestiaId): ContainerOwner? {
    val playerBestia = playerBestiaRepository.findByIdForUpdate(playerBestiaId)
      ?: throw PlayerBestiaNotFoundException(playerBestiaId)

    return playerBestia.takeIf { it.master.id == masterId }
  }

  override fun lockedChestOf(masterId: Long, structureId: Long): ContainerOwner? {
    val structure = playerStructureRepository.findByIdForUpdate(structureId)
      ?: error("No chest $structureId")

    return structure.takeIf { it.ownerMasterId == masterId && it.storage != null }
  }

  override fun save(owner: ContainerOwner) {
    when (owner) {
      is Master -> masterRepository.save(owner)
      is PlayerBestia -> playerBestiaRepository.save(owner)
      is PlayerStructure -> playerStructureRepository.save(owner)
      else -> error("Not a container owner of this slice: ${owner::class.simpleName}")
    }
  }

  override fun saveAndFlush(owner: ContainerOwner): ContainerOwner {
    return when (owner) {
      is Master -> masterRepository.saveAndFlush(owner)
      is PlayerBestia -> playerBestiaRepository.saveAndFlush(owner)
      is PlayerStructure -> playerStructureRepository.saveAndFlush(owner)
      else -> error("Not a container owner of this slice: ${owner::class.simpleName}")
    }
  }
}
