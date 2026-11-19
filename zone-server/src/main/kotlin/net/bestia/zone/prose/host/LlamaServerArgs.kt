package net.bestia.zone.prose.host

import net.bestia.zone.prose.ProseConfig

/** The command line of the llama.cpp server for the settings of this zone. */
object LlamaServerArgs {

  const val PORT = 8080

  fun of(llm: ProseConfig.Llm): List<String> {
    return listOf(
      // Fetched from Hugging Face on the first start and kept in the model cache.
      "-hf", llm.model,
      "-c", llm.contextSize.toString(),
      // One request at a time: more slots do not make a CPU write faster.
      "-np", "1",
      "-t", llm.threads.toString(),
      "-tb", llm.promptThreads.toString(),
      // Without these two the server keeps a second copy of the weights and gigabytes of old prompts.
      "--no-repack",
      "--cache-ram", "0",
      "--host", "0.0.0.0",
      "--port", PORT.toString(),
    )
  }
}
