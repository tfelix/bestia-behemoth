package net.bestia.zoneserver.account

import net.bestia.model.account.AccountRepository
import net.bestia.zoneserver.actor.bootstrap.ClusterBootStep
import org.springframework.stereotype.Component
import javax.transaction.Transactional

/**
 * When the cluster was not shut down properly is possible that some players
 * might have non valid IDs in the DB pointing to entities which don't exist
 * anymore. During cluster boot we remove those IDs.
 */
@Component
@Transactional
class ResetActiveBestiaClusterBootStep(
    private val accountRepository: AccountRepository
) : ClusterBootStep {
  override val bootStepName = "Reset active Player Bestia entity IDs"

  override fun execute() {
    accountRepository.findAllStream().forEach {
      it.activeBestia = null
      it.masterBestia?.entityId = 0
      accountRepository.save(it)
    }
  }
}