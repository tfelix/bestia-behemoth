using Godot;

namespace BestiaBehemothClient.Auth
{
  /// <summary>
  /// Where the token that skips the passkey ceremony is kept between runs of the game.
  /// </summary>
  /// <remarks>
  /// <para>
  /// Encrypted with a key derived from the machine id, which is obfuscation rather than protection:
  /// a desktop game has no secret store to put this in, and anything the game can decrypt on its own
  /// a program running as the same user can decrypt too. What it does buy is that the file is useless
  /// on another machine and is not a token sitting in plain text in a backup or a screenshot.
  /// </para>
  /// <para>
  /// That trade is bounded by what the token can do. It cannot create an account, enrol a passkey or
  /// change anything: it exchanges for the same short-lived zone ticket the ceremony produces, and the
  /// server retires it on every use, so a copy stops working the moment the real client starts the game.
  /// </para>
  /// </remarks>
  public static class RefreshTokenStore
  {
    private const string Path = "user://session.dat";

    /// <summary>The stored token, or null if there is none or it could not be read back.</summary>
    public static string Load()
    {
      if (!FileAccess.FileExists(Path))
      {
        return null;
      }

      using var file = FileAccess.OpenEncryptedWithPass(Path, FileAccess.ModeFlags.Read, Key());

      if (file == null)
      {
        // Wrong key (the file was copied from another machine), truncated write, corrupt file. None of
        // them are recoverable and all of them mean the same thing to the caller: sign in again.
        GD.PushWarning($"RefreshTokenStore: discarding unreadable {Path} ({FileAccess.GetOpenError()})");
        Clear();

        return null;
      }

      var token = file.GetAsText().Trim();

      return string.IsNullOrEmpty(token) ? null : token;
    }

    /// <summary>
    /// Replaces whatever was stored. Called after every successful exchange and refresh, because the
    /// server retires the token it was given and the successor is the only one that still works.
    /// </summary>
    public static void Save(string refreshToken)
    {
      if (string.IsNullOrEmpty(refreshToken))
      {
        Clear();

        return;
      }

      using var file = FileAccess.OpenEncryptedWithPass(Path, FileAccess.ModeFlags.Write, Key());

      if (file == null)
      {
        // Not fatal: the player is signed in either way, they just get the browser again next time.
        GD.PushWarning($"RefreshTokenStore: could not write {Path} ({FileAccess.GetOpenError()})");

        return;
      }

      file.StoreString(refreshToken);
    }

    public static void Clear()
    {
      if (FileAccess.FileExists(Path))
      {
        DirAccess.RemoveAbsolute(ProjectSettings.GlobalizePath(Path));
      }
    }

    /// <summary>
    /// Ties the file to this installation. Not a secret - <c>OS.GetUniqueId</c> is readable by anything
    /// running here - and not stable across a reinstall on some platforms, where an unreadable file is
    /// handled the same way a missing one is.
    /// </summary>
    private static string Key()
    {
      var machineId = OS.GetUniqueId();

      return string.IsNullOrEmpty(machineId) ? "bestia-behemoth-session" : machineId;
    }
  }
}
