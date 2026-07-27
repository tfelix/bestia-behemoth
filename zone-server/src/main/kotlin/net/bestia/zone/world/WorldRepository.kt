package net.bestia.zone.world

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface WorldRepository : JpaRepository<PersistedWorld, Long> {

  fun findByName(name: String): PersistedWorld?

  /**
   * The oldest world, which for now is *the* world.
   *
   * Ordered rather than "the only one" on purpose. A shard hosting several worlds is a plausible future, and a
   * query that would start returning an arbitrary row the day a second world appears is worse than one that
   * keeps returning the same one.
   */
  fun findFirstByOrderByIdAsc(): PersistedWorld?
}
