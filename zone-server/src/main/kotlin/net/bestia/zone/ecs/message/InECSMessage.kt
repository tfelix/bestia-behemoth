package net.bestia.zone.ecs.message

/**
 * Marker interface for messages fed INTO the ECS system to get processed for various reasons (e.g. most likely
 * those messages need direct or close interaction to the ECS while getting processed)
 *
 * TODO probably there is no need anymore for this whole system.
 */
interface InECSMessage
