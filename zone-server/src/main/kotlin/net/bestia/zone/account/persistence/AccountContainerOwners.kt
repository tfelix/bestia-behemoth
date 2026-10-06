package net.bestia.zone.account.persistence

import net.bestia.zone.account.MasterNotFoundException
import net.bestia.zone.account.PlayerBestiaNotFoundException
import net.bestia.zone.item.container.ContainerOwner
import net.bestia.zone.item.container.ContainerOwners
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Component

@Component
class AccountContainerOwners(
  private val masterRepository: MasterRepository,
  private val playerBestiaRepository: PlayerBestiaRepository,
) : ContainerOwners {

  override fun lockedMaster(masterId: Long): ContainerOwner {
    return masterRepository.findByIdForUpdate(masterId) ?: throw MasterNotFoundException()
  }

  override fun lockedPlayerBestia(playerBestiaId: PlayerBestiaId): ContainerOwner {
    return playerBestiaRepository.findByIdForUpdate(playerBestiaId)
      ?: throw PlayerBestiaNotFoundException(playerBestiaId)
  }

  override fun save(owner: ContainerOwner) {
    when (owner) {
      is Master -> masterRepository.save(owner)
      is PlayerBestia -> playerBestiaRepository.save(owner)
      else -> error("Not a container owner of this slice: ${owner::class.simpleName}")
    }
  }

  override fun saveAndFlush(owner: ContainerOwner): ContainerOwner {
    return when (owner) {
      is Master -> masterRepository.saveAndFlush(owner)
      is PlayerBestia -> playerBestiaRepository.saveAndFlush(owner)
      else -> error("Not a container owner of this slice: ${owner::class.simpleName}")
    }
  }
}
