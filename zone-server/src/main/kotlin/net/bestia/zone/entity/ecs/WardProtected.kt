package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Not owned by any player, but protected by a ward stone as if it were: a townsperson of a home village. The
 * ward and the damage gate read this rather than the townsfolk slice, which sits above them.
 */
data object WardProtected : Component
