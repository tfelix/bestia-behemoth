using System.Collections.Generic;
using System.Linq;
using BestiaBehemothClient.Bnet.Message.Map;
using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The client's copy of the terrain it has been sent, and the bookkeeping that keeps it honest.
  /// </summary>
  /// <remarks>
  /// Three jobs, all of which are really the same job - knowing exactly what is held and at what revision.
  ///
  /// <list type="number">
  /// <item><b>Answer a manifest.</b> Chunks already held at the announced revision are not requested, which is
  /// what turns a 375 kB re-entry into an area into nothing at all.</item>
  /// <item><b>Apply patches.</b> A swing's worth of removals is a couple of hundred bytes where the chunk
  /// is three thousand.</item>
  /// <item><b>Notice divergence.</b> A patch whose <c>FromRevision</c> is not what is held means this copy and
  /// the server's have parted company. The chunk is dropped and re-requested rather than patched, because a
  /// wrongly-patched chunk is invisible: the player walks into a wall that is not there and the bug report is
  /// incomprehensible.</item>
  /// </list>
  ///
  /// <para>
  /// In memory for the session only. Persisting it across sessions is what the revision numbers were designed
  /// for and is a straightforward addition, but it should wait for the server to persist its deltas - a cache
  /// keyed on a revision the server forgets on restart would eventually be trusted when it should not be.
  /// </para>
  ///
  /// <para><b>Read from more than one thread.</b> <see cref="TerrainRenderer"/> meshes on the thread pool and
  /// reads chunks and their scans straight out of here while this thread is still adding more, so the map itself
  /// has to be concurrent - a plain <c>Dictionary</c> resizing under a reader can hang or return nonsense, which
  /// is not a race that shows up in testing and then does in the field.
  /// </para>
  ///
  /// <para>
  /// The voxels inside a chunk are deliberately <i>not</i> synchronised. <see cref="ApplyPatch"/> writes bytes in
  /// place while a mesh job may be reading them, and the worst outcome is a mesh built from a mixture of the two
  /// revisions - byte writes do not tear, so no value is ever invented. That mesh is immediately superseded,
  /// because applying a patch also queues the chunk to be meshed again. Locking instead would put a mesh job's
  /// duration in the way of the network thread, to fix one stale frame.
  /// </para>
  /// </remarks>
  public sealed class ClientChunkStore : IChunkSource
  {
    private readonly System.Collections.Concurrent.ConcurrentDictionary<ChunkKey, Held> _held = new();

    /// <summary>Announced by the most recent manifest, whether held yet or not.</summary>
    private readonly Dictionary<ChunkKey, uint> _announced = new();

    private sealed class Held
    {
      internal VoxelChunk Chunk { get; init; }
      internal uint Revision { get; set; }

      /// <summary>
      /// The band scan, kept beside the chunk because meshing needs the neighbours' scans as well as its own.
      /// </summary>
      /// <remarks>
      /// Scanned once when the chunk lands rather than once per mesh. A chunk is meshed at least twice in
      /// practice - when it arrives, and again when a neighbour that was missing turns up - and its scan is read
      /// by all eight of its neighbours' mesh jobs too, so computing it on demand would repeat it about ten
      /// times. Thirty-two kilobytes against half a megabyte of voxels is a cheap way not to.
      /// </remarks>
      internal ChunkBands Bands { get; set; }
    }

    public int HeldCount => _held.Count;

    public int AnnouncedCount => _announced.Count;

    public VoxelChunk Get(ChunkKey key) => _held.TryGetValue(key, out var held) ? held.Chunk : null;

    public ChunkBands BandsOf(ChunkKey key) => _held.TryGetValue(key, out var held) ? held.Bands : null;

    public bool Holds(ChunkKey key, uint revision) =>
      _held.TryGetValue(key, out var held) && held.Revision == revision;

    /// <summary>
    /// Applies a manifest and returns what still has to be asked for.
    /// </summary>
    /// <remarks>
    /// A <c>Reset</c> manifest replaces the set outright, so anything held and not re-listed is dropped. That
    /// is the case that would otherwise leak: the server has stopped tracking those chunks and will send no
    /// more patches for them, so keeping them would mean holding terrain that quietly goes stale.
    /// </remarks>
    public List<ChunkKey> Reconcile(ChunkManifestSMSG manifest)
    {
      if (manifest.Reset)
      {
        _announced.Clear();

        var listed = manifest.Added.Select(added => added.Key).ToHashSet();
        foreach (var key in _held.Keys.Where(key => !listed.Contains(key)).ToList())
        {
          _held.TryRemove(key, out _);
        }
      }

      foreach (var key in manifest.Removed)
      {
        _announced.Remove(key);
        _held.TryRemove(key, out _);
      }

      var wanted = new List<ChunkKey>();

      foreach (var added in manifest.Added)
      {
        _announced[added.Key] = added.Revision;

        if (Holds(added.Key, added.Revision))
        {
          continue;
        }

        // Held at the wrong revision is not usable - drop it rather than keep something that will disagree
        // with the next patch.
        _held.TryRemove(added.Key, out _);
        wanted.Add(added.Key);
      }

      return wanted;
    }

    /// <summary>Stores a decoded chunk. Replaces whatever was held at that position.</summary>
    public void Put(ChunkKey key, VoxelChunk chunk, uint revision)
    {
      _held[key] = new Held { Chunk = chunk, Revision = revision, Bands = ChunkBands.Of(chunk) };
    }

    /// <summary>
    /// Applies a patch, or reports that the chunk must be re-requested.
    /// </summary>
    /// <returns>
    /// <c>true</c> if the patch was applied; <c>false</c> if the chunk is not held or has diverged, in which
    /// case it has been dropped and the caller should request it again.
    /// </returns>
    public bool ApplyPatch(ChunkPatchSMSG patch)
    {
      if (!_held.TryGetValue(patch.Key, out var held))
      {
        // Not a fault. A patch can legitimately arrive for a chunk that was announced but never requested,
        // and there is nothing to do about it - the manifest will offer the new revision.
        return false;
      }

      if (held.Revision != patch.FromRevision)
      {
        GD.PushWarning(
          $"[chunk] {patch.Key} diverged: holding rev {held.Revision}, patch builds on {patch.FromRevision}. " +
          "Discarding and re-requesting.");

        _held.TryRemove(patch.Key, out _);
        return false;
      }

      foreach (var removal in patch.Decode())
      {
        held.Chunk.ApplyRemoval(removal.Index, removal.RemainingOccupancy);
      }

      held.Revision = patch.ToRevision;

      // A removal moves run boundaries, so the cached scan is now wrong about where the surface can be.
      // Rescanning the whole chunk is a few dozen microseconds and cannot be subtly incorrect, which patching
      // the mask in place could easily be. Removal-only does not make an incremental update safe either: a
      // carve can destroy the last boundary in a column as easily as create one.
      held.Bands = ChunkBands.Of(held.Chunk);

      return true;
    }

    /// <summary>Whether this position is currently on offer, so a re-request is worth sending.</summary>
    public bool IsAnnounced(ChunkKey key) => _announced.ContainsKey(key);

    /// <summary>
    /// Everything currently held, as a snapshot.
    /// </summary>
    /// <remarks>
    /// A copy rather than the live keys, because the caller's next act is usually to reconcile a manifest, which
    /// mutates the very dictionary it would otherwise be iterating.
    /// </remarks>
    public List<ChunkKey> HeldKeys() => _held.Keys.ToList();

    public void Clear()
    {
      _held.Clear();
      _announced.Clear();
    }

    /// <summary>
    /// A one-line summary of a chunk's contents, for the debug output.
    /// </summary>
    public string Describe(ChunkKey key)
    {
      var chunk = Get(key);
      if (chunk == null)
      {
        return $"{key} not held";
      }

      var lowest = double.PositiveInfinity;
      var highest = double.NegativeInfinity;
      var empties = 0;

      for (var localY = 0; localY < chunk.Size; localY++)
      {
        for (var localX = 0; localX < chunk.Size; localX++)
        {
          var surface = chunk.SurfaceHeightAt(localX, localY);

          if (surface < 0.0)
          {
            empties++;
            continue;
          }

          if (surface < lowest) lowest = surface;
          if (surface > highest) highest = surface;
        }
      }

      var counts = new Dictionary<int, int>();
      foreach (var block in chunk.Blocks)
      {
        counts.TryGetValue(block, out var seen);
        counts[block] = seen + 1;
      }

      var top = counts
        .OrderByDescending(entry => entry.Value)
        .Take(4)
        .Select(entry => $"{BlockAppearance.Current.NameOf(entry.Key)}x{entry.Value}");

      var surfaces = double.IsInfinity(lowest)
        ? "no solid columns"
        : $"surface {lowest:F1}..{highest:F1} above chunk floor";

      var emptyNote = empties > 0 ? $", {empties} empty columns" : "";

      return $"{surfaces}{emptyNote}  {string.Join(" ", top)}";
    }
  }
}
