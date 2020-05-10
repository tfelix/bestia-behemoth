package net.bestia.model.battle

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PlayerAttackRepository : JpaRepository<PlayerBestiaAttack, Long> {

  /**
   * Return all usable attacks for a given Bestia. The attacks are sorted by
   * level requirement in ascending order. If not attacks where found an empty
   * list is returned.
   *
   */
  @Query("FROM PlayerBestiaAttack pa WHERE pa.playerBestia.id = :pbId ORDER BY pa.minLevel ASC")
  fun getAllAttacksForBestia(@Param("pbId") playerBestiaId: Long): List<PlayerBestiaAttack>
}
