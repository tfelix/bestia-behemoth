package net.bestia.worldgen.core

/**
 * The names of a `data class`'s primary-constructor properties, read out of its `toString()`.
 *
 * This exists to make a hand-written [ParamsDigest] safe. Seventeen params classes hold something like two
 * hundred and twenty tunables between them, `HistoryParams` alone has thirty-nine, and the failure mode of a
 * hand-written fold is a field nobody added - which is silent, because the digest still produces a number and
 * the number is still stable. Comparing [of] against `ParamsDigest.names` turns that into a test failure
 * naming the field.
 *
 * ### Why reading toString() is legitimate here and not in the digest
 *
 * [ParamsDigest]'s KDoc rejects `toString()` for the digest itself because the decimal rendering of a `Double`
 * is a property of the runtime, and the version gate has to hold across a JVM and a C# client. None of that
 * applies to this: it reads only the *field names*, and it runs in the same process as the class it is reading.
 * The values are discarded before they are looked at.
 *
 * ### The one constraint it imposes
 *
 * A tunable must be a **primary-constructor property**. Kotlin renders exactly those in a data class's
 * `toString`, so a `val` declared in the class body is invisible to this oracle *and* to the digest - and
 * therefore invisible twice, which is the one way a missing tunable could still slip through. That constraint
 * is stated in [ParamsDigest]'s KDoc as well, because this file is not where anybody will look for it.
 *
 * ### How the parse works, and what it deliberately does not handle
 *
 * Nested params render their own parenthesised `toString` inside the outer one - `basins=ClosedBasinParams(
 * spacing=75000.0, ...)` - so the nested content is stripped by paren depth before names are matched. What
 * survives is `basins=ClosedBasinParams`, which yields the name `basins` and correctly says nothing about the
 * nested class's own fields; those are checked against their own digest.
 *
 * It would be confused by a `String` tunable whose value contains `identifier=`, since Kotlin renders strings
 * unquoted. No params class has one, and the failure direction is safe: a phantom name makes the set comparison
 * fail loudly rather than pass while something is missing.
 */
object ParamsFields {

  private val FIELD = Regex("""(?:^|,\s*)(\w+)=""")

  fun of(params: Any): Set<String> {
    val rendered = params.toString()
    val open = rendered.indexOf('(')
    require(open >= 0 && rendered.endsWith(')')) {
      "${params::class.simpleName} does not render like a data class: $rendered"
    }

    return FIELD.findAll(topLevelOf(rendered.substring(open + 1, rendered.length - 1)))
      .map { it.groupValues[1] }
      .toSet()
  }

  /** [body] with everything inside a nested parenthesis removed, so only the outer fields remain. */
  private fun topLevelOf(body: String): String {
    val out = StringBuilder(body.length)
    var depth = 0

    for (c in body) {
      when {
        c == '(' -> depth++
        c == ')' -> depth--
        depth == 0 -> out.append(c)
      }
    }

    check(depth == 0) { "unbalanced parentheses in $body" }
    return out.toString()
  }
}
