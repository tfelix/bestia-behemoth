package net.bestia.zone.prose.host

import net.bestia.zone.prose.LlmClient

/**
 * Something that has a language model ready for as long as work is done with it. The text job does not know
 * whether that is a container of this zone or a node that lends its model.
 */
interface LlmHost {

  /** Makes the model ready, runs [work] with it, and lets go of the model again, also when [work] fails. */
  fun <T> withModel(work: (LlmClient) -> T): T
}
