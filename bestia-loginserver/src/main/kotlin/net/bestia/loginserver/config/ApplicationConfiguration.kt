package net.bestia.loginserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.BufferedImageHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import java.awt.image.BufferedImage

@Configuration
class ApplicationConfiguration {

  @Bean
  fun createImageHttpMessageConverter(): HttpMessageConverter<BufferedImage> {
    return BufferedImageHttpMessageConverter()
  }
}