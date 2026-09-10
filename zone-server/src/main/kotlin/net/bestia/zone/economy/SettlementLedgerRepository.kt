package net.bestia.zone.economy

import org.springframework.data.jpa.repository.JpaRepository

/**
 * `findAll()` for the boot load, on `ScorchRepository`'s reasoning: the table is bounded by how many
 * settlements are away from their reference *right now*, not by how many there are, because a town that
 * has decayed back deletes its row.
 */
interface SettlementLedgerRepository : JpaRepository<SettlementLedger, Int>
