package net.bestia.zone.spellbinding

import net.bestia.zone.casting.InscribedSpell
import java.time.Instant

/** Everything one binding holds, wherever it is bound. [id] is its row in `spell_binding`. */
data class BoundSpell(
  val id: Long,
  val owner: SpellBindingOwner,
  val spell: InscribedSpell,
  val trigger: BindingTrigger,
  val filter: BindingFilter,
  val potency: Int,
  val binder: BinderSnapshot,
  val boundAt: Instant,
  /** When an [BindingTrigger.AFTER_DELAY] binding fires; null for every other trigger. */
  val firesAt: Instant? = null,
)
