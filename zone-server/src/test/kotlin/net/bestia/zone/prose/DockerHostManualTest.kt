package net.bestia.zone.prose

import net.bestia.zone.prose.host.DockerLlmHost
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertTrue

/**
 * Starts the real model's container, asks it one thing and removes it again. Not part of the suite: it needs
 * Docker and the model. `PROSE_DOCKER_HOST` names another way to Docker than the socket, such as a socket proxy.
 *
 *     PROSE_REAL_HOST=true ./gradlew :zone-server:test --tests 'net.bestia.zone.prose.DockerHostManualTest' -i
 */
@EnabledIfEnvironmentVariable(named = "PROSE_REAL_HOST", matches = "true")
// A CI runner has no memory for the model, whatever its variables say.
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class DockerHostManualTest {

  @Test
  fun `the zone starts the model, gets an answer and removes the container`() {
    val defaults = ProseConfig.Llm()
    val llm = defaults.copy(
      dockerHost = System.getenv("PROSE_DOCKER_HOST") ?: defaults.dockerHost,
      network = System.getenv("PROSE_NETWORK"),
    )

    val answer = DockerLlmHost(ProseConfig(llm = llm)).withModel { it.chat(null, "Answer with the one word: ready", ProsePrompt.SAMPLING) }

    println("PROSE HOST answered: $answer")
    assertTrue(answer.isNotBlank())
  }
}
