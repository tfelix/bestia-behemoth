package net.bestia.messages.account

/**
 * Error which can happen during account registration.
 *
 * @author Thomas Felix
 */
enum class AccountRegistrationError {
  INVALID_DATA, USERNAME_INVALID, EMAIL_INVALID, KEY_INVALID, GENERAL_ERROR, NONE
}
