package net.bestia.zone.prose

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * A llama.cpp server, spoken to over its OpenAI-style chat endpoint. One request at a time: more do not make a
 * CPU write faster.
 */
class LlamaServerClient(
  baseUrl: String,
  override val model: String,
  /** A long text on a slow CPU takes minutes. */
  answerTimeout: Duration = Duration.ofMinutes(15),
) : LlmClient {

  private val http = RestClient.builder()
    .baseUrl(baseUrl)
    .requestFactory(JdkClientHttpRequestFactory(HttpClient.newHttpClient()).apply { setReadTimeout(answerTimeout) })
    .build()

  override fun chat(system: String?, user: String, sampling: LlmClient.Sampling): String {
    val messages = listOfNotNull(system?.let { Message("system", it) }, Message("user", user))
    val request = ChatRequest(
      messages, sampling.temperature, sampling.topP, sampling.minP, sampling.repeatPenalty, sampling.maxTokens,
    )

    val answer = http.post()
      .uri("/v1/chat/completions")
      .contentType(MediaType.APPLICATION_JSON)
      .body(request)
      .retrieve()
      .body(ChatAnswer::class.java)
    val text = answer?.choices?.firstOrNull()?.message?.content
      ?: error("The model answered without a text")

    answer.timings?.let {
      LOG.debug { "Read ${it.promptTokens} tokens at ${it.promptPerSecond.toInt()}/s, wrote ${it.writtenTokens} at ${"%.1f".format(it.writtenPerSecond)}/s" }
    }

    return text
  }

  /** True once the model is loaded and the server takes requests. */
  fun isReady(): Boolean {
    return runCatching { http.get().uri("/health").retrieve().toBodilessEntity().statusCode.is2xxSuccessful }.getOrDefault(false)
  }

  private class Message(val role: String, val content: String)

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private class ChatRequest(
    val messages: List<Message>,
    val temperature: Double,
    @get:JsonProperty("top_p") val topP: Double,
    @get:JsonProperty("min_p") val minP: Double?,
    @get:JsonProperty("repeat_penalty") val repeatPenalty: Double?,
    @get:JsonProperty("max_tokens") val maxTokens: Int,
  ) {
    /** Some models think aloud for minutes before they write. A model without that switch ignores it. */
    @get:JsonProperty("chat_template_kwargs")
    val chatTemplateKwargs = mapOf("enable_thinking" to false)
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private class ChatAnswer(val choices: List<Choice> = emptyList(), val timings: Timings? = null)

  @JsonIgnoreProperties(ignoreUnknown = true)
  private class Choice(val message: AnswerMessage? = null)

  @JsonIgnoreProperties(ignoreUnknown = true)
  private class AnswerMessage(val content: String? = null)

  @JsonIgnoreProperties(ignoreUnknown = true)
  private class Timings(
    @JsonProperty("prompt_n") val promptTokens: Int = 0,
    @JsonProperty("prompt_per_second") val promptPerSecond: Double = 0.0,
    @JsonProperty("predicted_n") val writtenTokens: Int = 0,
    @JsonProperty("predicted_per_second") val writtenPerSecond: Double = 0.0,
  )

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
