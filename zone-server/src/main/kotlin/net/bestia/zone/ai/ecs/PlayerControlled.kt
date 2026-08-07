package net.bestia.zone.ai.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Marks the one entity a player is currently driving, so the AI leaves it alone.
 *
 * This is what makes "idle behaviour" a real concept rather than a wish. A master owns several bestias; exactly
 * one of them is under the player's hands at a time, and the rest should be getting on with whatever standing
 * order they were given. Both halves want the same [AiAgent], so the difference cannot be whether the component
 * is attached — it has to be a flag the think and act stages consult.
 *
 * Kept as a marker component rather than a field on [AiAgent] because the entity being controlled is a *session*
 * fact, written by the message handler that switches focus, not something the AI owns. And deliberately not
 * `Dirtyable`: which of their own creatures a player is driving is something their client already knows.
 *
 * Note it does not stop perception. An idle bestia that is being attacked while its owner is busy elsewhere must
 * still notice, so that the moment control is handed back — or the moment it is left alone again — its memory
 * reflects the world rather than whatever was true when the player took over.
 */
object PlayerControlled : Component
