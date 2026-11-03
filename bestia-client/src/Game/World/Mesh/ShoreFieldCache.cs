using System.Collections.Generic;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// The <see cref="ShoreField"/> of each chunk something has stood on, kept until the ground it was read from
  /// changes.
  /// </summary>
  /// <remarks>
  /// The mesher builds its own field per job on a worker thread, and that one is not shared: a job can finish
  /// after the chunk has changed again, and handing its field on would need a revision check this does anyway.
  /// So a probe that needs one builds it here, on first use, from the same chunks by the same exact procedure -
  /// the field is a pure function of the voxels, so both come out the same.
  ///
  /// <para>
  /// <b>Kept honest by the band scans.</b> The store replaces a chunk's <see cref="ChunkBands"/> whenever the
  /// chunk lands or is patched, and a field reads only the chunk and its 26 neighbours. Remembering which scan
  /// each of those had, and comparing the references on every read, notices any change to the ground under a
  /// field without the store having to say anything - 27 lookups, against an eighth of a mesh job to rebuild.
  /// </para>
  ///
  /// <para>
  /// Main thread only, like everything that probes the ground.
  /// </para>
  /// </remarks>
  public sealed class ShoreFieldCache
  {
    /// <summary>Entries kept before the whole cache is dropped.</summary>
    /// <remarks>More than the chunks under everything in view.</remarks>
    private const int Capacity = 256;

    private readonly Dictionary<ChunkKey, Entry> _entries = new();

    private sealed class Entry
    {
      internal ShoreField Field;
      internal ChunkBands[] Bands;
    }

    /// <summary>
    /// The field for the chunk at <paramref name="key"/>, or <c>null</c> where no water is within reach of it.
    /// </summary>
    /// <param name="key">Already normalised by <paramref name="wrap"/>.</param>
    public ShoreField Get(IChunkSource source, ChunkKey key, ChunkWrap wrap, BlockAppearance appearance)
    {
      if (_entries.TryGetValue(key, out var entry) && Matches(entry.Bands, source, key, wrap))
      {
        return entry.Field;
      }

      if (_entries.Count >= Capacity)
      {
        _entries.Clear();
      }

      // The scans are taken before the build, so a change landing between the two makes the entry stale rather
      // than wrongly current.
      var bands = BandsAround(source, key, wrap);
      var field = source.Get(key) == null ? null : ShoreField.Build(source, key, wrap, appearance);
      _entries[key] = new Entry { Field = field, Bands = bands };

      return field;
    }

    public void Clear() => _entries.Clear();

    private static ChunkBands[] BandsAround(IChunkSource source, ChunkKey key, ChunkWrap wrap)
    {
      var bands = new ChunkBands[27];
      var i = 0;
      for (var dz = -1; dz <= 1; dz++)
      {
        for (var dy = -1; dy <= 1; dy++)
        {
          for (var dx = -1; dx <= 1; dx++)
          {
            bands[i++] = source.BandsOf(wrap.Normalise(new ChunkKey(key.X + dx, key.Y + dy, key.Z + dz)));
          }
        }
      }

      return bands;
    }

    private static bool Matches(ChunkBands[] bands, IChunkSource source, ChunkKey key, ChunkWrap wrap)
    {
      var i = 0;
      for (var dz = -1; dz <= 1; dz++)
      {
        for (var dy = -1; dy <= 1; dy++)
        {
          for (var dx = -1; dx <= 1; dx++)
          {
            var neighbour = wrap.Normalise(new ChunkKey(key.X + dx, key.Y + dy, key.Z + dz));
            if (!ReferenceEquals(bands[i++], source.BandsOf(neighbour)))
            {
              return false;
            }
          }
        }
      }

      return true;
    }
  }
}
