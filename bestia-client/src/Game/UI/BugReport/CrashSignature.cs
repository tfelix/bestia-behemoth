using System;
using System.Diagnostics;
using System.Linq;
using System.Security.Cryptography;
using System.Text;

namespace BestiaBehemothClient.Game.UI.BugReport
{
  /// <summary>
  /// What makes two exceptions the same crash: the exception type and the methods it was thrown through.
  /// </summary>
  /// <remarks>
  /// Line numbers and the message are left out, so a crash keeps its signature across builds and across the
  /// values in its message; the api-server groups reports with equal signatures into one issue.
  /// </remarks>
  public static class CrashSignature
  {
    private const int FramesUsed = 5;

    public static string Of(Exception exception)
    {
      var frames = new StackTrace(exception, false).GetFrames()
        .Select(frame => frame.GetMethod())
        .Where(method => method != null)
        .Take(FramesUsed)
        .Select(method => $"{method!.DeclaringType?.FullName}.{method.Name}");

      var key = exception.GetType().FullName + "|" + string.Join("|", frames);
      return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(key))).ToLowerInvariant();
    }
  }
}
