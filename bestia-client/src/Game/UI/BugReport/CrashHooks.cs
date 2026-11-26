using System;
using System.IO;
using System.Text.Json;
using System.Threading.Tasks;
using Godot;

namespace BestiaBehemothClient.Game.UI.BugReport
{
  /// <summary>
  /// Writes an exception that no Godot callback caught - one on the socket thread or in a forgotten task - to the
  /// crash report queue, where <c>crash_reports.gd</c> sends it from.
  /// </summary>
  /// <remarks>
  /// Exceptions in Godot callbacks never get here: Godot reports them as errors, and the crash logger takes those.
  /// The file is written at once, because an unhandled exception usually ends the process.
  /// A <see cref="RefCounted"/> only so GDScript can create one and call <see cref="Install"/>.
  /// </remarks>
  public partial class CrashHooks : RefCounted
  {
    private static bool _installed;
    private static string _queueDir = "";
    private static string _clientVersion = "";

    public void Install(string queueDir, string clientVersion)
    {
      if (_installed)
      {
        return;
      }

      _installed = true;
      _queueDir = queueDir;
      _clientVersion = clientVersion;
      AppDomain.CurrentDomain.UnhandledException += (_, args) =>
      {
        if (args.ExceptionObject is Exception exception)
        {
          Write(exception, args.IsTerminating);
        }
      };
      TaskScheduler.UnobservedTaskException += (_, args) =>
      {
        Write(args.Exception, fatal: false);
        args.SetObserved();
      };
    }

    private static void Write(Exception exception, bool fatal)
    {
      try
      {
        var signature = CrashSignature.Of(exception);
        var report = new
        {
          signature,
          text = exception.ToString(),
          clientVersion = _clientVersion,
          // A fatal one gets the session's log attached on the next start, when the log is complete.
          fatal,
        };
        var name = $"{DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()}-{signature[..12]}.json";
        File.WriteAllText(Path.Combine(_queueDir, name), JsonSerializer.Serialize(report));
      }
      catch (Exception)
      {
        // Nothing left to report to: failing here must not hide the original exception.
      }
    }
  }
}
