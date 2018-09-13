package net.bestia.zoneserver.configuration

import net.bestia.entity.component.interceptor.ActorUpdateComponentInterceptor
import net.bestia.entity.component.interceptor.Interceptor
import net.bestia.entity.component.interceptor.InterceptorComposite
import net.bestia.messages.MessageApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for the component interceptor.
 *
 * @author Thomas Felix
 */
@Configuration
class ComponentInterceptorConfiguration {

  /**
   * Adds a new default interceptor to the interceptor class.
   */
  @Bean
  fun defaultInterceptor(msgApi: MessageApi): Interceptor {

    val interceptor = InterceptorComposite(emptyList())
    interceptor.addDefaultInterceptor(ActorUpdateComponentInterceptor(msgApi))

    return interceptor
  }
}
