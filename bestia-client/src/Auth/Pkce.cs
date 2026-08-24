using System;
using System.Security.Cryptography;
using System.Text;

namespace BestiaBehemothClient.Auth
{
  /// <summary>
  /// Proof Key for Code Exchange (RFC 7636).
  /// </summary>
  /// <remarks>
  /// Needed even though Bestia is both the authorization server and the only client. The callback
  /// arrives on the loopback interface, which is shared with every other process on this machine,
  /// so holding the authorization code must not be sufficient on its own. The verifier never leaves
  /// this process, and that is what binds the code to the client that started the login.
  /// </remarks>
  public static class Pkce
  {
    /// <summary>
    /// 32 bytes rendered as base64url, i.e. 43 characters - the shortest verifier RFC 7636 allows,
    /// and already 256 bits.
    /// </summary>
    private const int VerifierBytes = 32;

    public static string CreateVerifier()
    {
      return Base64Url(RandomNumberGenerator.GetBytes(VerifierBytes));
    }

    public static string CreateState()
    {
      return Base64Url(RandomNumberGenerator.GetBytes(VerifierBytes));
    }

    public static string Challenge(string verifier)
    {
      var digest = SHA256.HashData(Encoding.ASCII.GetBytes(verifier));

      return Base64Url(digest);
    }

    private static string Base64Url(byte[] value)
    {
      return Convert.ToBase64String(value)
        .Replace('+', '-')
        .Replace('/', '_')
        .TrimEnd('=');
    }
  }
}
