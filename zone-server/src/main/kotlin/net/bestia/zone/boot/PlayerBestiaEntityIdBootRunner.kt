package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ecs.core.EntityIdGenerator
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Gives every player bestia without a stored entity id one, before anybody can select its master. A bestia is
 * given its id at creation, so only rows written before the column existed are found here.
 */
@Component
@Order(109)
class PlayerBestiaEntityIdBootRunner(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val entityIdGenerator: EntityIdGenerator,
) : CommandLineRunner {

  @Transactional
  override fun run(vararg args: String?) {
    val withoutId = playerBestiaRepository.findAllByEntityIdIsNull()
    if (withoutId.isEmpty()) {
      return
    }

    withoutId.forEach { it.entityId = entityIdGenerator.nextId() }

    LOG.info { "Gave ${withoutId.size} player bestia(s) a stored entity id" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
