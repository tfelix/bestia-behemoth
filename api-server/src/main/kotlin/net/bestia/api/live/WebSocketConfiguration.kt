package net.bestia.api.live

import net.bestia.api.auth.CompanionProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfiguration(
  private val handler: CompanionSocketHandler,
  private val properties: CompanionProperties,
) : WebSocketConfigurer {

  override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
    registry.addHandler(handler, "/v1/companion/ws").setAllowedOrigins(*properties.allowedOrigins.toTypedArray())
  }
}
