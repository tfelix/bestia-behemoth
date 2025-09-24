package net.bestia.zone

import jakarta.annotation.PreDestroy
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.stereotype.Component

// Spring will call this method just before the bean is destroyed, when the app is shutting down.
@Component
class ShutdownHandler {

  @PreDestroy
  fun onShutdown() {
    println("JVM is shutting down. Saving in-memory data...")
    // persistInMemoryData()
  }
}

@Component
class ShutdownListener : ApplicationListener<ContextClosedEvent> {

  override fun onApplicationEvent(event: ContextClosedEvent) {
    println("Spring context is shutting down.")
    // persistInMemoryData()
  }
}

// Spring beans may already be gone by the time this runs, so avoid relying on autowired services here.
@Component
class ShutdownHookRegistrar {

  init {
    Runtime.getRuntime().addShutdownHook(Thread {
      println("JVM shutdown hook triggered.")
      // persistInMemoryData()
    })
  }
}