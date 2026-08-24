package net.bestia.login.recovery

import net.bestia.login.util.SecureTokens
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class RecoveryCodeService(
  private val recoveryCodes: RecoveryCodeRepository
) {

  /**
   * Replaces the account's whole set and returns the new codes in the clear. This is the only
   * moment they exist outside the player's own records.
   */
  @Transactional
  fun reissue(accountId: Long, count: Int): List<String> {
    recoveryCodes.deleteAllByAccountId(accountId)

    val codes = List(count) { generate() }

    recoveryCodes.saveAll(
      codes.map { RecoveryCode(accountId = accountId, codeHash = hash(it)) }
    )

    return codes
  }

  @Transactional
  fun redeem(accountId: Long, rawCode: String): Boolean {
    return recoveryCodes.redeem(accountId, hash(rawCode), LocalDateTime.now()) == 1
  }

  @Transactional(readOnly = true)
  fun remainingFor(accountId: Long): Long {
    return recoveryCodes.countByAccountIdAndUsedAtIsNull(accountId)
  }

  /**
   * Six groups of four, drawn from an alphabet with no I, L, O or U: 120 bits, and nothing in it
   * that a player can misread off a printout or confuse with a digit.
   */
  private fun generate(): String {
    // Masking to 5 bits is uniform because the alphabet is exactly 32 symbols wide.
    val body = SecureTokens.randomBytes(CODE_LENGTH)
      .map { ALPHABET[it.toInt() and 0x1F] }
      .joinToString("")

    return body.chunked(GROUP_SIZE).joinToString("-")
  }

  private fun hash(rawCode: String): String {
    return SecureTokens.base64Url(SecureTokens.sha256(normalize(rawCode)))
  }

  companion object {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ0123456789"
    private const val CODE_LENGTH = 24
    private const val GROUP_SIZE = 4

    /** Players retype these, so grouping, case and stray whitespace must not decide the outcome. */
    fun normalize(rawCode: String): String {
      return rawCode.uppercase().filter { it in ALPHABET }
    }
  }
}
