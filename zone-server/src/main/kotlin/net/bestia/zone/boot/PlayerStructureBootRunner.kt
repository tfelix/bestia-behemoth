package net.bestia.zone.boot

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.persistence.PlayerStructureRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Loads what players have built, so [net.bestia.zone.prop.PlayerStructureSource] can answer per chunk
 * column from memory rather than querying the table from the tick thread.
 *
 * Grouped with [WorldObjectDivergenceBootRunner] (`@Order(3)`) under "things about the world" - both exist so
 * that materialising a column asks nothing of the database. Unlike that one this needs nothing off
 * [net.bestia.zone.world.WorldService]: a structure is named by its own row, not by a lattice cell, so there is
 * no version to check it against.
 */
@Component
@Order(4)
class PlayerStructureBootRunner(
  private val registry: PlayerStructureRegistry,
  private val structures: PlayerStructureRepository,
  private val masters: MasterRepository,
) : CommandLineRunner {

  @Transactional
  override fun run(vararg args: String?) {
    nameOwnerAccounts()
    registry.loadAll()
  }

  /** A structure is given its owner's account when it is placed; rows written before that are named here. */
  private fun nameOwnerAccounts() {
    val unnamed = structures.findAllByOwnerAccountIdIsNull()
    if (unnamed.isEmpty()) {
      return
    }

    val accountOfMaster = masters.findAllById(unnamed.map { it.ownerMasterId }.toSet())
      .associate { it.id to it.account.id }
    unnamed.forEach { it.ownerAccountId = accountOfMaster[it.ownerMasterId] }
  }
}
