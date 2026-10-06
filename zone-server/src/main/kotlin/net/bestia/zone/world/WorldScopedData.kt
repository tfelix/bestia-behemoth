package net.bestia.zone.world

/**
 * Rows another slice keeps that only mean something in the world they were written for. [WorldProvisioning]
 * wipes them with the world row, in one transaction, in `@Order` order.
 */
interface WorldScopedData {

  fun wipe()
}
