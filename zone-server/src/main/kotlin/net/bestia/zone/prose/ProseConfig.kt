package net.bestia.zone.prose

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "prose")
data class ProseConfig(

  /** Whether the zone writes texts for a new world with a language model. Off, every text stays its template. */
  val enabled: Boolean = false,

  /** The languages to write, English first: the others are translated from it. */
  val locales: List<String> = listOf("en"),

  /** The most time the job may spend on one language over the life of a world. What is left stays a template. */
  val capPerLocale: Duration = Duration.ofHours(3),

  /** How often the model is asked for one text before the text is given up. */
  val maxAttempts: Int = 3,
)
