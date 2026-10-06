package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Marker: this entity reaches clients through the per-chunk static batch, not through per-component messages.
 *
 * The discriminator for the third of three independent questions - who exists, what survives a restart, and
 * how it reaches the client - and it is deliberately *only* the third. A generated tree and a player-built wall
 * answer the first two completely differently and this one identically, which is the whole reason it is a
 * marker of its own rather than a property of how the entity was made or how it is stored.
 */
object StaticSync : Component
