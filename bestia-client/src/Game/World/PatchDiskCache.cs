using System;
using System.Collections.Generic;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Keeps coarse patch payloads on disk between sessions, so a region visited once costs nothing to revisit.
  /// </summary>
  /// <remarks>
  /// <b>Why this is safe when <see cref="ClientChunkStore"/> deliberately refuses to persist.</b> A chunk is
  /// keyed on a revision the server forgets when it restarts, so a stored copy could be trusted when it should
  /// not be. A patch has no revision at all: it is a pure function of the server's heightfield, which no player
  /// edit can move, so the only thing that can invalidate one is the world itself changing.
  ///
  /// <para>
  /// That is what <c>WorldInfoSMSG.SurfacePatchVersion</c> is for. It folds the world row's generation identity
  /// with the payload format, so a regenerated world - or a changed wire format - lands in a different
  /// directory and the old one is simply never read again. Caching against <c>ChunkEngineVersion</c> instead
  /// would have looked like it worked and drawn the previous world's terrain.
  /// </para>
  ///
  /// <para>
  /// Stores the payload exactly as it arrived, still deflated. Inflating on save would triple the disk for
  /// nothing: reading is followed immediately by a decode either way.
  /// </para>
  ///
  /// <para>
  /// Every operation is best-effort. A cache that cannot be read is a slower first frame; a cache that throws
  /// is a client that does not start, and no amount of stored terrain is worth that.
  /// </para>
  /// </remarks>
  public sealed class PatchDiskCache
  {
    private const string CacheRoot = "user://patchcache/";

    /// <summary>
    /// How many payloads one world's directory may hold before the oldest are dropped.
    /// </summary>
    /// <remarks>
    /// About two and a half kilobytes each, so this is roughly 25 MB - a few hours of walking. Bounded because
    /// a 128 km world holds a quarter of a million level-0 patches, and "never invalidated" is not a reason to
    /// grow without limit.
    /// </remarks>
    private const int MaxStored = 10_000;

    private string _dir = "";

    /// <summary>Names present on disk, so a miss costs no file system call at all.</summary>
    private readonly HashSet<string> _stored = new();

    public bool IsOpen => _dir.Length > 0;

    public int StoredCount => _stored.Count;

    /// <summary>
    /// Points the cache at a world, reading the index of what a previous session left for it.
    /// </summary>
    /// <param name="version">
    /// <c>WorldInfoSMSG.SurfacePatchVersion</c>. Zero means the server did not send one, and nothing is stored
    /// at all rather than stored under a key that every world would share.
    /// </param>
    public void Open(uint version)
    {
      if (version == 0)
      {
        Close();
        GD.Print("[patchcache] server sent no patch version; not persisting");
        return;
      }

      var dir = $"{CacheRoot}{version:x8}/";
      if (dir == _dir)
      {
        return;
      }

      _dir = dir;
      _stored.Clear();

      try
      {
        DirAccess.MakeDirRecursiveAbsolute(dir);
        Index(dir);
      }
      catch (Exception e)
      {
        GD.PushWarning($"[patchcache] could not open {dir}: {e.Message}");
        _dir = "";
        return;
      }

      GD.Print($"[patchcache] {_stored.Count} patches held for world {version:x8}");
    }

    public void Close()
    {
      _dir = "";
      _stored.Clear();
    }

    public bool Holds(PatchKey key) => IsOpen && _stored.Contains(key.ToString());

    /// <summary>The stored payload, still deflated, or null if there is none or it could not be read.</summary>
    public byte[] Read(PatchKey key)
    {
      if (!Holds(key))
      {
        return null;
      }

      try
      {
        using var file = FileAccess.Open(PathOf(key), FileAccess.ModeFlags.Read);
        if (file == null)
        {
          // On disk according to the index but not readable: drop it from the index so the next manifest
          // asks the server for it rather than reporting a hit forever.
          _stored.Remove(key.ToString());
          return null;
        }

        return file.GetBuffer((long)file.GetLength());
      }
      catch (Exception e)
      {
        GD.PushWarning($"[patchcache] could not read {key}: {e.Message}");
        _stored.Remove(key.ToString());
        return null;
      }
    }

    public void Write(PatchKey key, byte[] payload)
    {
      if (!IsOpen || payload == null || payload.Length == 0 || _stored.Count >= MaxStored)
      {
        return;
      }

      try
      {
        using var file = FileAccess.Open(PathOf(key), FileAccess.ModeFlags.Write);
        if (file == null)
        {
          return;
        }

        file.StoreBuffer(payload);
        _stored.Add(key.ToString());
      }
      catch (Exception e)
      {
        GD.PushWarning($"[patchcache] could not store {key}: {e.Message}");
      }
    }

    private string PathOf(PatchKey key) => $"{_dir}{key}.bin";

    private void Index(string dir)
    {
      using var access = DirAccess.Open(dir);
      if (access == null)
      {
        return;
      }

      foreach (var name in access.GetFiles())
      {
        if (name.EndsWith(".bin", StringComparison.Ordinal))
        {
          _stored.Add(name[..^4]);
        }
      }
    }
  }
}
