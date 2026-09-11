package net.bestia.zone.ai.rumour

import org.springframework.data.jpa.repository.JpaRepository

/**
 * `findAll()` for the boot load, on `SettlementLedgerRepository`'s reasoning: the table is bounded by
 * what has happened recently rather than by the size of the world, because a rumour deletes its row when
 * it expires.
 */
interface RumourRepository : JpaRepository<Rumour, Long>
