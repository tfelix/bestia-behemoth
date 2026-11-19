package net.bestia.zone.prose

/** A language model that answers a chat. The job talks to this, never to a server or a container. */
interface LlmClient {

  /** The name stored with every text, so a text can be traced to what wrote it. */
  val model: String

  /** @param system the standing rules, or null for a model that is told everything in [user] */
  fun chat(system: String?, user: String, sampling: Sampling): String

  data class Sampling(
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int,
    val minP: Double? = null,
    val repeatPenalty: Double? = null,
  )
}
