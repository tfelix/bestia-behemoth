package net.bestia.zone.dialog.conversation

import net.bestia.zone.util.EntityId

/**
 * Who is asking.
 *
 * Most topics are a pure function of who is answering and do not look at this at all. An action does:
 * a shop has to know where to send the window, and where the player is standing decides whose prices
 * are in it.
 */
class Asker(val accountId: Long, val entityId: EntityId)
