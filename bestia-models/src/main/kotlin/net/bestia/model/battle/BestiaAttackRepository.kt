package net.bestia.model.battle

import net.bestia.model.domain.BestiaAttack

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface BestiaAttackRepository : org.springframework.data.repository.Repository<BestiaAttack, Int> {

  /**
   * Return all usable attacks for a given Bestia. The attacks are sorted by
   * level requirement in ascending order. If not attacks where found an empty
   * list is returned.
   *
   */
  @Query("FROM BestiaAttack ba WHERE ba.bestia.id = :bestiaId ORDER BY ba.minLevel ASC")
  fun getAllAttacksForBestia(@Param("bestiaId") bestiaId: Int): List<BestiaAttack>
}
