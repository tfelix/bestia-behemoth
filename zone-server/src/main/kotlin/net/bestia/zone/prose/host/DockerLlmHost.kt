package net.bestia.zone.prose.host

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.prose.LlamaServerClient
import net.bestia.zone.prose.LlmClient
import net.bestia.zone.prose.ProseConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Clock
import java.time.Duration

/**
 * Runs the language model in a container that this zone starts when it has texts to write and removes when it
 * is done. Nobody starts it by hand and it never runs beside the game. The zone needs the Docker socket for it.
 */
@Component
@ConditionalOnProperty("prose.llm.host", havingValue = "DOCKER", matchIfMissing = true)
class DockerLlmHost(
  private val config: ProseConfig,
  private val clock: Clock = Clock.systemUTC(),
) : LlmHost {

  override fun <T> withModel(work: (LlmClient) -> T): T {
    return connect().use { docker ->
      removeLeftOver(docker)
      val network = config.llm.network ?: ownNetworkOf(docker)
      val containerId = start(docker, network)
      try {
        work(readyClient(docker, containerId, network))
      } finally {
        remove(docker, containerId)
      }
    }
  }

  private fun connect(): DockerClient {
    val settings = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(config.llm.dockerHost).build()
    val http = ZerodepDockerHttpClient.Builder().dockerHost(settings.dockerHost).build()

    return DockerClientImpl.getInstance(settings, http)
  }

  /** A zone that was killed while it wrote leaves its container behind. */
  private fun removeLeftOver(docker: DockerClient) {
    try {
      docker.removeContainerCmd(CONTAINER_NAME).withForce(true).exec()
      LOG.warn { "Removed the model container an earlier run left behind" }
    } catch (_: NotFoundException) {
      // The usual case.
    }
  }

  /**
   * The Docker network of this zone when it runs in a container itself, where its host name is its container
   * id. Null for a zone on the host. So the same settings work in both places.
   */
  private fun ownNetworkOf(docker: DockerClient): String? {
    return try {
      docker.inspectContainerCmd(InetAddress.getLocalHost().hostName).exec().networkSettings.networks.keys.firstOrNull()
    } catch (_: NotFoundException) {
      null
    } catch (_: UnknownHostException) {
      null
    }
  }

  private fun start(docker: DockerClient, network: String?): String {
    val llm = config.llm
    pullIfMissing(docker, llm.image)

    val served = ExposedPort.tcp(LlamaServerArgs.PORT)
    val host = HostConfig.newHostConfig()
      .withMemory(llm.memory.toBytes())
      .withBinds(Bind(llm.modelCache, Volume(MODEL_CACHE_PATH)))
    llm.cpus?.let { host.withCpusetCpus(it) }
    if (network == null) {
      // Loopback only, on a port Docker picks: the model is this zone's, not the network's.
      host.withPortBindings(PortBinding(Ports.Binding.bindIp(LOOPBACK), served))
    } else {
      // Beside the zone, which reaches it by name. No port leaves the network.
      host.withNetworkMode(network)
    }

    val container = docker.createContainerCmd(llm.image)
      .withName(CONTAINER_NAME)
      .withCmd(LlamaServerArgs.of(llm))
      .withEnv("LLAMA_CACHE=$MODEL_CACHE_PATH")
      .withExposedPorts(served)
      .withHostConfig(host)
      .exec()
    docker.startContainerCmd(container.id).exec()
    LOG.info { "Started the model container ${container.id.take(12)} with ${llm.model}" }

    return container.id
  }

  private fun pullIfMissing(docker: DockerClient, image: String) {
    try {
      docker.inspectImageCmd(image).exec()
    } catch (_: NotFoundException) {
      LOG.info { "Pulling $image" }
      docker.pullImageCmd(image).start().awaitCompletion()
    }
  }

  /** Waits until the model is loaded. On the first start that includes fetching it. */
  private fun readyClient(docker: DockerClient, containerId: String, network: String?): LlmClient {
    val client = LlamaServerClient(urlOf(docker, containerId, network), config.llm.model)
    val giveUpAt = clock.instant().plus(config.llm.startTimeout)

    while (!client.isReady()) {
      check(docker.inspectContainerCmd(containerId).exec().state.running == true) {
        "The model container stopped before it was ready. Its log is in `docker logs $CONTAINER_NAME` until it is removed."
      }
      check(clock.instant().isBefore(giveUpAt)) { "The model was not ready within ${config.llm.startTimeout}" }
      Thread.sleep(POLL.toMillis())
    }
    LOG.info { "The model is ready" }

    return client
  }

  private fun urlOf(docker: DockerClient, containerId: String, network: String?): String {
    if (network != null) {
      return "http://$CONTAINER_NAME:${LlamaServerArgs.PORT}"
    }

    val bindings = docker.inspectContainerCmd(containerId).exec().networkSettings.ports.bindings
    val port = bindings[ExposedPort.tcp(LlamaServerArgs.PORT)]?.firstOrNull()?.hostPortSpec
      ?: error("Docker gave the model container no port")

    return "http://$LOOPBACK:$port"
  }

  private fun remove(docker: DockerClient, containerId: String) {
    runCatching { docker.removeContainerCmd(containerId).withForce(true).exec() }
      .onSuccess { LOG.info { "Removed the model container" } }
      .onFailure { LOG.error(it) { "Could not remove the model container $containerId; remove it by hand" } }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** One name, so a container left behind is found and a second one cannot start beside it. */
    const val CONTAINER_NAME = "bestia-llm"
    const val MODEL_CACHE_PATH = "/models"
    const val LOOPBACK = "127.0.0.1"
    val POLL: Duration = Duration.ofSeconds(3)
  }
}
