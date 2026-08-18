package net.bestia.zone.ecs.entity

import net.bestia.bnet.proto.VisualComponentProto

/**
 * Which client-side catalogue an [EntityVisual]'s id points into.
 *
 * Deliberately coarse: a kind exists to pick a catalogue, not to describe an entity. Anything the
 * server needs to *know* about a thing lives in its own component - a mob's species is
 * [EntityVisual.id] only because the species catalogue is what the client draws from as well.
 */
enum class VisualKind {
  BESTIA,
  ITEM,

  /** Spell and ground effects. Short-lived, never persisted. */
  EFFECT;

  fun toBnet(): VisualComponentProto.VisualKind = when (this) {
    BESTIA -> VisualComponentProto.VisualKind.BESTIA
    ITEM -> VisualComponentProto.VisualKind.ITEM
    EFFECT -> VisualComponentProto.VisualKind.EFFECT
  }
}
