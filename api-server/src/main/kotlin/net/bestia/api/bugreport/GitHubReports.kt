package net.bestia.api.bugreport

/** The private reports repo, as far as publishing a report needs it. */
interface GitHubReports {

  /** Commits [files], keyed by their path in the repo, on top of the branch. Returns the commit's sha. */
  fun commit(message: String, files: Map<String, ByteArray>): String

  /** Returns the new issue's number. */
  fun openIssue(title: String, body: String, labels: List<String>): Int
}
