package net.bestia.zone.postal.office

import net.bestia.zone.util.EntityId

/**
 * Where a post office window is open: the postmaster or the player-built office the client names, and the office
 * it serves. [office] is null while it is closed: a postmaster off shift, or a town whose sign fell.
 */
data class PostCounter(val entityId: EntityId, val office: PostOffices.Office?)
