package net.bestia.zone.ai.rumour

/** How many ways there are to say one kind of news, and which words a phrasing may fill in. */
data class RumourLine(
  val variants: Int,
  val slots: Set<String>,
)
