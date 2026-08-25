package net.bestia.zone.cartography.chart

import net.bestia.zone.BestiaEvent

/**
 * A master's charts have been written to: what they can see of the world may have changed.
 *
 * Published by [ChartService] and listened for by the tile service, which memoises a master's charted area and
 * would otherwise go on serving the area from before the write. That matters more than a normally-stale cache
 * would, because of what the client does with the answer: an uncharted tile is a 404, and the client remembers
 * a 404 for as long as it holds the same charts. So a tile refused in the moment after a survey is not fog for
 * a moment - it is fog until the player's charts change again, which is the next survey.
 *
 * Carries the master rather than the account: charts belong to a character, and it is the character's coverage
 * the tile service keys on.
 */
class ChartsChangedEvent(source: Any, val masterId: Long) : BestiaEvent(source)
