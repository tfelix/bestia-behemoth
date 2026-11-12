package net.bestia.zone.battle.mark

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/** Whom a hunter's mark names now; null once it has run out. Kept on the hunter, see `HuntersMarked`. */
class MarkedPrey(var preyId: EntityId? = null) : Component
