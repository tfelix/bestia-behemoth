package net.bestia.zone.spellbinding

import net.bestia.zone.ecs.core.Component

/** A binding on the ground: lives on its own glyph entity, which only the binder is shown. */
data class GroundSpellBinding(val bound: BoundSpell) : Component
