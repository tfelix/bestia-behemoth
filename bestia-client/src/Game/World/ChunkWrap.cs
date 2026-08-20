using System;
using BestiaBehemothClient.Bnet.Message.Map;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// How many chunks the world is across, and which of its horizontal axes are seams rather than edges.
  /// </summary>
  /// <remarks>
  /// The client mirror of the server's <c>WorldWrap</c>. A world with <c>wrapX</c> set has no eastern edge:
  /// chunk zero is the eastern neighbour of the last column, so every address has more than one spelling, and
  /// anything that looks a chunk up - or measures how far apart two of them are - has to agree on which
  /// spelling to use.
  ///
  /// <para>
  /// The server normalises every address it touches, so whatever arrives over the wire is already canonical.
  /// This exists for the addresses the client derives for <i>itself</i>: the eight neighbours of a chunk being
  /// meshed, the chunk under a world position. Those are the ones that can name a column one past the edge, and
  /// left alone a mesh job at the seam asks for a chunk it will never be sent, records it as a missing
  /// neighbour, and clamps its own edge column in its place - a flat wall where the server sees continuous
  /// ground. It also means a wrapped address from the server and a locally computed one are different
  /// dictionary keys, so the same chunk can be held twice under two names.
  /// </para>
  ///
  /// <para>
  /// <b>The vertical axis is never wrapped</b> - up is not a loop, and voxel zero is sea level rather than a
  /// floor, which is why <see cref="ChunkKey.Z"/> is signed in the first place.
  /// </para>
  ///
  /// <para>
  /// A <c>readonly struct</c> so it can be captured by value into a mesh job rather than read from live config
  /// inside one. Jobs run off the main thread and a fresh <c>WorldInfoSMSG</c> reconfigures the renderer
  /// wholesale, so a job that read the extent as it went could mesh half of one world and half of the next.
  /// </para>
  /// </remarks>
  public readonly struct ChunkWrap
  {
    /// <summary>A world of edges rather than seams, which is also what an unconfigured renderer has.</summary>
    public static readonly ChunkWrap None = new(0, 0, false, false);

    public ChunkWrap(int chunksAcross, int chunksDown, bool wrapX, bool wrapY)
    {
      ChunksAcross = chunksAcross;
      ChunksDown = chunksDown;

      // A wrap needs an extent to wrap by. Folding that into the flags here keeps every method below total,
      // rather than making each one re-check that the world it was told about makes sense.
      WrapX = wrapX && chunksAcross > 0;
      WrapY = wrapY && chunksDown > 0;
    }

    public int ChunksAcross { get; }

    public int ChunksDown { get; }

    public bool WrapX { get; }

    public bool WrapY { get; }

    /// <summary>
    /// The world's chunk grid as described by <paramref name="worldInfo"/>.
    /// </summary>
    /// <remarks>
    /// The same arithmetic as <c>WorldConfig.chunkExtent</c> and <c>WorldWrap.chunksAcross</c> on the server:
    /// the world's extent in metres over one chunk's, rounded up. Ceiling rather than truncation because a
    /// world whose width is not a whole number of chunks still has that last partial column, and the server
    /// counts it.
    /// </remarks>
    public static ChunkWrap Of(WorldInfoSMSG worldInfo)
    {
      if (worldInfo == null)
      {
        return None;
      }

      var chunkExtent = worldInfo.ChunkSize * worldInfo.VoxelSizeMetres;
      if (chunkExtent <= 0.0)
      {
        return None;
      }

      var across = (int)Math.Ceiling(worldInfo.WidthCells * worldInfo.CellSizeMetres / chunkExtent);
      var down = (int)Math.Ceiling(worldInfo.HeightCells * worldInfo.CellSizeMetres / chunkExtent);

      return new ChunkWrap(across, down, worldInfo.WrapX, worldInfo.WrapY);
    }

    /// <summary>The canonical spelling of <paramref name="key"/>: inside the world on every wrapped axis.</summary>
    public ChunkKey Normalise(ChunkKey key)
    {
      if (!WrapX && !WrapY)
      {
        return key;
      }

      return new ChunkKey(
        WrapX ? FloorMod(key.X, ChunksAcross) : key.X,
        WrapY ? FloorMod(key.Y, ChunksDown) : key.Y,
        key.Z);
    }

    /// <summary>Chunks from <paramref name="from"/> to <paramref name="to"/> along x, whichever way is shorter.</summary>
    public int DeltaX(int from, int to) => Shortest(to - from, WrapX, ChunksAcross);

    /// <summary>Chunks from <paramref name="from"/> to <paramref name="to"/> along y, whichever way is shorter.</summary>
    public int DeltaY(int from, int to) => Shortest(to - from, WrapY, ChunksDown);

    private static int Shortest(int delta, bool wraps, int extent)
    {
      if (!wraps)
      {
        return delta;
      }

      // Folded into (-extent/2, extent/2], so two chunks 90% of the world apart the long way are 10% apart the
      // short way - which is the way a player would walk, and the one a view radius has to measure.
      return FloorMod(delta + extent / 2, extent) - extent / 2;
    }

    private static int FloorMod(int value, int extent)
    {
      var remainder = value % extent;

      return remainder < 0 ? remainder + extent : remainder;
    }
  }
}
