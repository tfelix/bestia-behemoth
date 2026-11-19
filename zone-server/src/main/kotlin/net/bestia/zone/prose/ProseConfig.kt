package net.bestia.zone.prose

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

@ConfigurationProperties(prefix = "prose")
data class ProseConfig(

  /** Whether the zone writes texts for a new world with a language model. Off, every text stays its template. */
  val enabled: Boolean = false,

  /** The languages to write, English first: the others are translated from it. */
  val locales: List<String> = listOf("en", "de"),

  /** The most time the job may spend on one language over the life of a world. What is left stays a template. */
  val capPerLocale: Duration = Duration.ofHours(3),

  /** How often the model is asked for one text before the text is given up. */
  val maxAttempts: Int = 3,

  val llm: Llm = Llm(),
) {

  data class Llm(

    /** Where the model runs: in a container this zone starts and stops, or behind [url]. */
    val host: Host = Host.DOCKER,

    /** A llama.cpp server somebody else runs, for [Host.URL]: another node that lends its model. */
    val url: String? = null,

    /** The llama.cpp server image. Pin a digest for a release. */
    val image: String = "ghcr.io/ggml-org/llama.cpp:server",

    /** The model as a Hugging Face repository and quantisation. The server fetches it once into [modelCache]. */
    val model: String = "unsloth/gemma-4-E4B-it-GGUF:Q4_K_M",

    /** The Docker volume the model file is kept in, so it is fetched once and not per world. */
    val modelCache: String = "bestia-llm-models",

    /** Threads for writing and for reading the prompt. More writing threads than fast cores makes it slower. */
    val threads: Int = 6,
    val promptThreads: Int = 10,

    /** Tokens of prompt and answer together. The longest brief with its answer is well under this. */
    val contextSize: Int = 4096,

    /** The most memory the container may take. */
    val memory: DataSize = DataSize.ofGigabytes(8),

    /** The CPUs the container may use, as Docker's `--cpuset-cpus`. Null for all. */
    val cpus: String? = null,

    /** How this zone reaches Docker. */
    val dockerHost: String = "unix:///var/run/docker.sock",

    /**
     * The Docker network to put the container on, for a zone that runs in a container itself: it then reaches
     * the model by the container's name. Null for a zone on the host, which gets a loopback port.
     */
    val network: String? = null,

    /** How long the container may take to be ready. The first start fetches the model, about 5 GB. */
    val startTimeout: Duration = Duration.ofMinutes(45),
  )

  enum class Host { DOCKER, URL }
}
