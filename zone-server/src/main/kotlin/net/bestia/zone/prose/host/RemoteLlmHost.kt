package net.bestia.zone.prose.host

import net.bestia.zone.prose.LlamaServerClient
import net.bestia.zone.prose.LlmClient
import net.bestia.zone.prose.ProseConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * A model somebody else runs: this zone only uses it. On a cluster this is how a zone that plans the texts uses
 * the model of a node that was started to write them.
 */
@Component
@ConditionalOnProperty("prose.llm.host", havingValue = "URL")
class RemoteLlmHost(private val config: ProseConfig) : LlmHost {

  override fun <T> withModel(work: (LlmClient) -> T): T {
    val url = requireNotNull(config.llm.url) { "prose.llm.host is URL, so prose.llm.url must name the server" }

    return work(LlamaServerClient(url, config.llm.model))
  }
}
