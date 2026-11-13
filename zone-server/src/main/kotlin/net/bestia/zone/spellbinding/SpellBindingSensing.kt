package net.bestia.zone.spellbinding

import net.bestia.zone.battle.potency.Potency
import net.bestia.zone.casting.sense.SensedBindings
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * What Magic Sense finds of Spell Bindings: glyphs on the ground and bound items lying there. A binding hides, so a
 * sweep finds it only within `range x Edge(P sense - P binding)`. A bound item in a hand or a bag is not sensed.
 */
@Component
class SpellBindingSensing(
  private val book: SpellBindingBook,
  private val glyphs: SpellBindingGlyphs,
  private val notices: SpellBindingNotices,
) : SensedBindings {

  override fun around(world: World, at: Vec3L, range: Long, potency: Int): List<SensedBindings.SensedBinding> {
    val found = ArrayList<SensedBindings.SensedBinding>()
    fun reaches(bound: BoundSpell, position: Vec3L): Boolean {
      return position.distance(at).toDouble() <= range * Potency.edge(potency, bound.potency)
    }

    world.query(GroundSpellBinding::class, Position::class).each { glyph ->
      val bound = get<GroundSpellBinding>().bound
      val position = get<Position>().toVec3L()
      if (reaches(bound, position)) found += sensed(bound, position, glyph)
    }

    if (book.hasItemBindings()) {
      world.query(GroundItemStack::class, Position::class).each { _ ->
        val bound = book.byItem(get<GroundItemStack>().uniqueId)?.bound ?: return@each
        val position = get<Position>().toVec3L()
        if (reaches(bound, position)) found += sensed(bound, position, glyphId = null)
      }
    }

    return found
  }

  override fun reveal(world: World, glyphId: EntityId, viewers: Set<AccountId>, seconds: Int) {
    val bound = world.get(glyphId, GroundSpellBinding::class)?.bound ?: return
    glyphs.reveal(glyphId, bound.owner.accountId, viewers, seconds)
  }

  private fun sensed(bound: BoundSpell, at: Vec3L, glyphId: EntityId?): SensedBindings.SensedBinding {
    return SensedBindings.SensedBinding(
      at = at,
      potency = bound.potency,
      element = notices.spellElement(bound.spell.skillId),
      spellName = notices.spellName(bound.spell.skillId),
      reading = "${bound.trigger.words}, ${bound.filter.words}, bound by ${bound.owner.masterName}",
      glyphId = glyphId,
    )
  }
}
