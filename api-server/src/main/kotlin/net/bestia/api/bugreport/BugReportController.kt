package net.bestia.api.bugreport

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.server.ResponseStatusException

/** Bug and crash reports from the game client, sent with its ticket. */
@RestController
class BugReportController(private val intake: BugReportIntake) {

  @PostMapping("/v1/companion/bug-reports", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun report(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @RequestParam text: String,
    @RequestParam(defaultValue = "manual") kind: String,
    @RequestParam(required = false) signature: String?,
    @RequestParam(required = false) masterId: Long?,
    @RequestParam(required = false) clientVersion: String?,
    request: MultipartHttpServletRequest,
  ): ResponseEntity<Void> {
    val reportKind = kindOf(kind)
    val submission = BugReportIntake.Submission(
      accountId = accountId,
      masterId = masterId,
      kind = reportKind,
      signature = signatureOf(reportKind, signature),
      clientVersion = clientVersionOf(clientVersion),
      text = textOf(text),
      files = attachmentsOf(request),
    )

    return when (intake.submit(submission)) {
      BugReportIntake.Outcome.ACCEPTED -> ResponseEntity.accepted().build()
      BugReportIntake.Outcome.RATE_LIMITED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .build()
    }
  }

  private fun kindOf(kind: String): BugReport.Kind {
    return BugReport.Kind.entries.firstOrNull { it.name.equals(kind, ignoreCase = true) }
      ?: throw badRequest("Unknown report kind '$kind'")
  }

  private fun signatureOf(kind: BugReport.Kind, signature: String?): String? {
    if (kind == BugReport.Kind.MANUAL) {
      return null
    }
    if (signature == null || !SIGNATURE.matches(signature)) {
      throw badRequest("A crash report needs a hex SHA-256 signature")
    }

    return signature
  }

  private fun clientVersionOf(clientVersion: String?): String? {
    if (clientVersion != null && clientVersion.length > MAX_CLIENT_VERSION_LENGTH) {
      throw badRequest("Client version is too long")
    }

    return clientVersion
  }

  private fun textOf(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_TEXT_LENGTH) {
      throw badRequest("A report needs a text of at most $MAX_TEXT_LENGTH characters")
    }

    return trimmed
  }

  private fun attachmentsOf(request: MultipartHttpServletRequest): List<BugReportAttachment.Prepared> {
    return BugReportAttachment.entries.mapNotNull { attachment ->
      val part = request.getFile(attachment.part) ?: return@mapNotNull null
      attachment.prepare(part.bytes) ?: throw badRequest("The '${attachment.part}' part is too large or not what it must be")
    }
  }

  private fun badRequest(reason: String): ResponseStatusException {
    return ResponseStatusException(HttpStatus.BAD_REQUEST, reason)
  }

  private companion object {
    val SIGNATURE = Regex("^[0-9a-f]{64}$")
    const val MAX_TEXT_LENGTH = 20_000
    const val MAX_CLIENT_VERSION_LENGTH = 64
    const val RETRY_AFTER_SECONDS = "3600"
  }
}
