package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.AccountRepository
import net.bestia.zone.bestia.PlayerBestiaEntityFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Imports the mobs from the YML resources into the database.
 */
@Component
@Order(101)
class EntitySpawnerBootRunner(
  private val accountRepository: AccountRepository,
  private val playerBestiaEntityFactory: PlayerBestiaEntityFactory,
) : CommandLineRunner {
  override fun run(vararg args: String?) {
    LOG.info { "Spawning persisted entities..." }
  }

  private fun setupAndSpawnPlayerBestias() {
    LOG.info { "Spawning bestias for ${accountRepository.count()} accounts" }

    accountRepository.streamAllWithMasterAndBestias().use { stream ->
      stream.forEach { account ->
        account.master.forEach { master ->
          master.bestias.ownedBestias.forEach { pb ->

            LOG.debug { "Spawning player bestia ${pb.id} for account ${account.id}" }
            
            playerBestiaEntityFactory.createPlayerBestiaEntity(pb)
          }
        }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}