package net.bestia.zone.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PersistedStatusEffectRepository : JpaRepository<PersistedStatusEffect, Long> {

  fun findAllByOwnerEntityId(ownerEntityId: Long): List<PersistedStatusEffect>

  fun findAllByOwnerEntityIdIn(ownerEntityIds: Collection<Long>): List<PersistedStatusEffect>

  /**
   * Safe as a bulk statement, unlike anything on [PersistedEntityRepository]: this entity owns no
   * children, so there is no cascade for the bulk delete to bypass. That is exactly why the deletes over
   * there are `deleteAll(findAll...)` extensions rather than `@Query`s - see [deleteAllByEntityIdIn].
   */
  @Modifying
  @Query("DELETE FROM PersistedStatusEffect e WHERE e.ownerEntityId IN :ownerEntityIds")
  fun deleteByOwnerEntityIdIn(@Param("ownerEntityIds") ownerEntityIds: Collection<Long>)
}
