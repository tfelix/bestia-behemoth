package net.bestia.loginserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.BufferedImageHttpMessageConverter
import java.awt.image.BufferedImage
import org.springframework.http.converter.HttpMessageConverter

@Configuration
class ApplicationConfiguration {

  @Bean
  fun createImageHttpMessageConverter(): HttpMessageConverter<BufferedImage> {
    return BufferedImageHttpMessageConverter()
  }
}