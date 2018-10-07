package net.bestia.zoneserver.entity.component

/**
 * Saves the AI state of the Bestia and der underlying AI engine used.
 * Currently only simple random walk is implemented.
 */
data class AiComponent(
    override val entityId: Long
) : Component