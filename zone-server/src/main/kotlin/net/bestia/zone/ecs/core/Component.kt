package net.bestia.zone.ecs.core

/**
 * Marker interface for all components. Components are passive data holders; all
 * gameplay logic lives in [System]s. This mirrors the marker-interface
 * approach of the existing `net.bestia.zone.ecs.Component` so a later migration
 * stays mechanical.
 */
interface Component
