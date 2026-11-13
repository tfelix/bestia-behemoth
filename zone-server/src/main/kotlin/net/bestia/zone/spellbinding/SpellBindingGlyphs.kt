package net.bestia.zone.spellbinding

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** The glyph a ground binding lives on. Only its binder is shown it; Magic Sense is the one way for others to see it. */
@Component
class SpellBindingGlyphs(
  private val visibility: EntityVisibility,
) {

  fun spawn(world: World, bound: BoundSpell, at: Vec3L): EntityId {
    val glyph = world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, EntityVisual(VisualKind.EFFECT, GLYPH_VISUAL_ID))
      add(id, GroundSpellBinding(bound))
    }
    visibility.conceal(glyph, setOf(bound.owner.accountId))

    return glyph
  }

  companion object {
    /** `18_spell_binding_glyph.tres` in the client's effect DB. */
    const val GLYPH_VISUAL_ID = 18L
  }
}
