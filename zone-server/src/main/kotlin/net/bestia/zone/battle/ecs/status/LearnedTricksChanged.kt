package net.bestia.zone.battle.ecs.status

import net.bestia.zone.ecs.core.Component

/**
 * The Learned Trick essences of a master or a player's Bestia grant other attacks than before: gear went on or off,
 * or an essence went into worn gear. Its skill list has to be sent again, which the casting slice does.
 */
data object LearnedTricksChanged : Component
