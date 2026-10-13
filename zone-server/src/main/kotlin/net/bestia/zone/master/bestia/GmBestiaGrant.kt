package net.bestia.zone.master.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * Gives every master a GM account creates the GM test bestia (`mob/gm-bestia.yml`). The master gets it rather
 * than the account because the master owns bestias, and does not exist yet when the account is created.
 *
 * Written to the database only; the bestia enters the world when the master is selected, like any other it owns.
 * Never throws: the master is already committed, so a failure here must not read as a failed creation.
 */
@Component
class GmBestiaGrant(
  private val connectionInfoService: ConnectionInfoService,
  private val bestiaCatalogue: BestiaCatalogue,
  private val playerBestiaFactory: PlayerBestiaFactory,
  transactionManager: PlatformTransactionManager,
) {

  // Its own transaction, so a failure rolls back the grant and cannot mark the caller's transaction rollback-only.
  private val ownTransaction = TransactionTemplate(transactionManager).apply {
    propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
  }

  /** Hands the GM bestia to [masterId], standing at [at], when [accountId] holds [Authority.TEST_BESTIA]. */
  fun grantIfGm(accountId: AccountId, masterId: Long, at: Vec3L): Boolean {
    if (Authority.TEST_BESTIA !in connectionInfoService.getGrantedAuthorities(accountId)) {
      return false
    }

    return try {
      val species = bestiaCatalogue.byIdentifier(SPECIES)

      ownTransaction.execute {
        playerBestiaFactory.create(
          masterId = masterId,
          playerBestiaCreateData = PlayerBestiaFactory.PlayerBestiaCreateData(
            bestiaIdentifier = SPECIES,
            spawnPosition = at,
            // The species' level, so the YML stays the one place it is set.
            level = species.level
          )
        )
      }

      LOG.info { "Gave master $masterId of GM account $accountId the $SPECIES at level ${species.level}" }
      true
    } catch (e: Exception) {
      LOG.error(e) { "Could not give master $masterId of GM account $accountId the $SPECIES; they go without" }
      false
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** The `identifier` in `mob/gm-bestia.yml`. */
    const val SPECIES = "gm_bestia"
  }
}
