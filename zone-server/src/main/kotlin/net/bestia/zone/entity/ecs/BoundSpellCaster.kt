package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.AccountId

/**
 * The stand-in that casts a fired Spell Binding with the binder's frozen stats, so the cast runs the usual pipeline
 * while the binder may be far away or logged out. It answers for [ownerAccountId] and casts at [potency].
 */
class BoundSpellCaster(
  val ownerAccountId: AccountId,
  val potency: Int,
) : Component
