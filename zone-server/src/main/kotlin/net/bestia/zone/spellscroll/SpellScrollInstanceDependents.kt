package net.bestia.zone.spellscroll

import net.bestia.zone.item.container.ItemInstanceDependents
import net.bestia.zone.spellscroll.persistence.SpellScrollInscriptionRepository
import org.springframework.stereotype.Component

/** A scroll's attack is a row keyed by its item instance, so it goes before the instance. */
@Component
class SpellScrollInstanceDependents(
  private val repository: SpellScrollInscriptionRepository,
  private val book: SpellScrollBook,
) : ItemInstanceDependents {

  override fun deleteFor(instanceIds: Collection<Long>) {
    repository.deleteAllByItemInstanceIdIn(instanceIds)
    book.forget(instanceIds)
  }
}
