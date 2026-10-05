package net.bestia.login.webauthn

/** What a ceremony was started as, which is the only thing it may be finished as. */
enum class CeremonyType {
  /** A brand new account with its first passkey. */
  REGISTRATION,

  /** A further passkey on an account that has just signed in. */
  ADD_CREDENTIAL,

  /** A replacement passkey after a recovery code was redeemed. */
  RECOVERY,

  ASSERTION
}
