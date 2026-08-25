package net.bestia.zone.world.fire

import org.springframework.data.jpa.repository.JpaRepository

/**
 * `findAll()` for the boot load, on `WorldObjectDivergenceRepository`'s own reasoning: the table is bounded by
 * how much ground is scarred *right now*, not by world size, because a healed scar deletes its row.
 *
 * That self-pruning is what makes a full scan fine here where it would not be for a table that only ever grows.
 */
interface ScorchRepository : JpaRepository<ScorchMark, Long>
