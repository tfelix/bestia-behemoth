package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.spellbinding.SpellBindingBook
import net.bestia.zone.spellbinding.SpellBindingGlyphs
import net.bestia.zone.spellbinding.persistence.SpellBindingRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Lays every waiting ground Spell Binding back down as its glyph and fills the [SpellBindingBook]. A binding has no
 * timer, so it has to outlast a restart. Before the socket opens, so no binder sees a glyph appear late.
 */
@Component
@Order(113)
class SpellBindingBootRunner(
  private val world: World,
  private val repository: SpellBindingRepository,
  private val book: SpellBindingBook,
  private val glyphs: SpellBindingGlyphs,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    book.startIdsAfter(repository.maxId())

    val rows = repository.findAllByItemInstanceIdIsNull()
    for (row in rows) {
      val position = row.position ?: continue
      val bound = row.toBoundSpell()
      book.add(SpellBindingBook.Entry(bound, glyphId = glyphs.spawn(world, bound, position), position = position))
    }

    LOG.info { "Laid down ${rows.size} spell binding(s)" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
