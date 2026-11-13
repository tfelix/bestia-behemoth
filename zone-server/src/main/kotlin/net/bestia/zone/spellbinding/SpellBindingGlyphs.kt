package net.bestia.zone.spellbinding

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The glyph a ground binding lives on. Only its binder is shown it; Magic Sense is the one way for others to see it. */
@Component
class SpellBindingGlyphs(
  private val visibility: EntityVisibility,
  private val clock: Clock = Clock.systemUTC(),
) {

  private class Revealed(val binder: AccountId, val viewers: Set<AccountId>, val until: Instant)

  private val revealed = ConcurrentHashMap<EntityId, Revealed>()

  fun spawn(world: World, bound: BoundSpell, at: Vec3L): EntityId {
    val glyph = world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, EntityVisual(VisualKind.EFFECT, GLYPH_VISUAL_ID))
      add(id, GroundSpellBinding(bound))
    }
    visibility.conceal(glyph, setOf(bound.owner.accountId))

    return glyph
  }

  /** Shows [glyph] to [viewers] beside its [binder] for [seconds]; a later sweep widens and renews it. */
  fun reveal(glyph: EntityId, binder: AccountId, viewers: Set<AccountId>, seconds: Int) {
    val earlier = revealed[glyph]?.viewers.orEmpty()
    val shownTo = earlier + viewers + binder
    revealed[glyph] = Revealed(binder, shownTo, clock.instant().plusSeconds(seconds.toLong()))
    // Widening needs a reveal first: concealing again only takes accounts away.
    visibility.reveal(glyph)
    visibility.conceal(glyph, shownTo)
  }

  fun shownTo(glyph: EntityId): Set<AccountId> {
    return revealed[glyph]?.viewers.orEmpty()
  }

  fun hasRevealed(): Boolean {
    return revealed.isNotEmpty()
  }

  /** Hides every glyph whose sensing ran out from all but its binder again. Between ticks. */
  fun hideExpired(world: World) {
    val now = clock.instant()
    revealed.entries.removeIf { (glyph, shown) ->
      val expired = !now.isBefore(shown.until)
      if (expired && world.isAlive(glyph)) {
        visibility.reveal(glyph)
        visibility.conceal(glyph, setOf(shown.binder))
      }
      expired
    }
  }

  companion object {
    /** `18_spell_binding_glyph.tres` in the client's effect DB. */
    const val GLYPH_VISUAL_ID = 18L
  }
}
