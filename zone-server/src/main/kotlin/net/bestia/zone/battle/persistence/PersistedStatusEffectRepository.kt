package net.bestia.zone.battle.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PersistedStatusEffectRepository : JpaRepository<PersistedStatusEffect, Long> {

  fun findAllByOwnerEntityId(ownerEntityId: Long): List<PersistedStatusEffect>

  fun findAllByOwnerEntityIdIn(ownerEntityIds: Collection<Long>): List<PersistedStatusEffect>

  @Query("SELECT DISTINCT e.ownerEntityId FROM PersistedStatusEffect e")
  fun findOwnerEntityIds(): List<Long>

  /**
   * Safe as a bulk statement, unlike anything on [net.bestia.zone.persistence.PersistedEntityRepository]: this entity owns no
   * children, so there is no cascade for the bulk delete to bypass. That is exactly why the deletes over
   * there are `deleteAll(findAll...)` extensions rather than `@Query`s - see [net.bestia.zone.persistence.deleteAllByEntityIdIn].
   */
  @Modifying
  @Query("DELETE FROM PersistedStatusEffect e WHERE e.ownerEntityId IN :ownerEntityIds")
  fun deleteByOwnerEntityIdIn(@Param("ownerEntityIds") ownerEntityIds: Collection<Long>)
}
