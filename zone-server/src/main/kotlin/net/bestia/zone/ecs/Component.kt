package net.bestia.zone.ecs

/**
 * The ECS component marker now lives in the ecs2 engine. This alias keeps the many
 * `net.bestia.zone.ecs.Component` component/data classes compiling unchanged while they are backed
 * by the new sparse-set [net.bestia.zone.ecs2.World].
 */
typealias Component = net.bestia.zone.ecs2.Component
