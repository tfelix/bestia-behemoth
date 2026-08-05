package net.bestia.zone.world.prop

import org.springframework.data.jpa.repository.JpaRepository

/**
 * `findAll()` for the boot load - bounded by how many objects have ever been depleted across this world's
 * lifetime, not by world population, so a full scan is fine (mirrors `LootItemEntityPersister.loadAll`'s own
 * reasoning for the same shape of table).
 */
interface WorldObjectDivergenceRepository : JpaRepository<WorldObjectDivergence, Long>
