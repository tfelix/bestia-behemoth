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
  EFFECT,

  /**
   * Something built, drawn from the prop catalogue: [EntityVisual.id] is a
   * [net.bestia.zone.world.prop.StaticEntityKind] ordinal.
   *
   * The odd one out, because a finished structure normally reaches a client on the per-chunk static batch
   * rather than as an entity at all. A construction site cannot: its progress and health change while
   * somebody watches, and `ZoneEngine` keeps anything carrying `StaticSync` out of the entity channel
   * entirely. So a site is an ordinary entity that happens to be drawn from the prop catalogue.
   */
  STRUCTURE;

  fun toBnet(): VisualComponentProto.VisualKind = when (this) {
    BESTIA -> VisualComponentProto.VisualKind.BESTIA
    ITEM -> VisualComponentProto.VisualKind.ITEM
    EFFECT -> VisualComponentProto.VisualKind.EFFECT
    STRUCTURE -> VisualComponentProto.VisualKind.STRUCTURE
  }
}
