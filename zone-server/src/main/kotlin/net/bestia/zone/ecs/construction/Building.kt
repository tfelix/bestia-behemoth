package net.bestia.zone.ecs.construction

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/**
 * This entity is working on a construction site.
 *
 * On the **worker** - a master or their bestia - rather than on the site, because it is the worker that can
 * walk away, die, or be told to stop, and because several of them may end up on one site.
 *
 * Not `Dirtyable`: what a client draws is the site filling in, and that is the site's own message. A builder
 * who is merely standing there looks no different from one who is not.
 */
data class Building(val siteEntityId: EntityId) : Component
