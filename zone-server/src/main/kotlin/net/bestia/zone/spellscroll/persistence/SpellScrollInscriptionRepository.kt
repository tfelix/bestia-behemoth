package net.bestia.zone.spellscroll.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SpellScrollInscriptionRepository : JpaRepository<SpellScrollInscription, Long> {

  @Query(
    "select new net.bestia.zone.spellscroll.persistence.InscribedScroll(s.itemInstance.id, s.skillId, s.level) " +
      "from SpellScrollInscription s"
  )
  fun findAllInscribed(): List<InscribedScroll>

  fun deleteAllByItemInstanceIdIn(itemInstanceIds: Collection<Long>)
}
