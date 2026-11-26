package net.bestia.api

import net.bestia.api.bugreport.GitHubReports
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestClientException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Records what would reach the reports repo; its own class for the reason [RecordingPush] is. */
@TestConfiguration(proxyBeanMethods = false)
class RecordingGitHub {

  data class Issue(val number: Int, val title: String, val body: String, val labels: List<String>)

  val commits = CopyOnWriteArrayList<Map<String, ByteArray>>()
  val issues = CopyOnWriteArrayList<Issue>()

  /** While true, every call fails as an unreachable GitHub would. */
  @Volatile
  var down = false

  private val numbers = AtomicInteger()

  @Bean
  @Primary
  fun recordingGitHub(): GitHubReports {
    return object : GitHubReports {
      override fun commit(message: String, files: Map<String, ByteArray>): String {
        failIfDown()
        commits += files
        return "c0ffee${commits.size}"
      }

      override fun openIssue(title: String, body: String, labels: List<String>): Int {
        failIfDown()
        val issue = Issue(numbers.incrementAndGet(), title, body, labels)
        issues += issue
        return issue.number
      }
    }
  }

  private fun failIfDown() {
    if (down) {
      throw RestClientException("GitHub is down")
    }
  }
}
