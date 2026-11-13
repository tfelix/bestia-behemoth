package net.bestia.zone.scry

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/** The eye floating where a Sage's Scry looks, tied to the Sage who cast it. */
class ScryEye(val casterId: EntityId) : Component
