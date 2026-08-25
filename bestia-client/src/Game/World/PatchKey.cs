using System;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The address of one coarse surface patch: a detail level and a position on that level's grid.
  /// </summary>
  /// <remarks>
  /// Mirrors the server's <c>PatchPos</c>. Levels are anchored at the fine end - level 0 is
  /// <see cref="PatchGrid.FinestMetres"/> per sample in every world - so a patch stored on disk keeps its
  /// meaning across a world resize, and what a bigger world changes is how many levels sit above the finest.
  ///
  /// <para>
  /// Deliberately not a <see cref="ChunkKey"/> with a level bolted on: a chunk address has a <c>Z</c> and a
  /// patch has none. A patch is a heightfield, one surface per column, and cannot express a hole.
  /// </para>
  /// </remarks>
  public readonly struct PatchKey : IEquatable<PatchKey>
  {
    public int Level { get; }
    public int X { get; }
    public int Y { get; }

    public PatchKey(int level, int x, int y)
    {
      Level = level;
      X = x;
      Y = y;
    }

    public static PatchKey FromProto(global::Bnet.PatchPos pos) => new((int)pos.Level, pos.X, pos.Y);

    public global::Bnet.PatchPos ToProto() => new() { Level = (uint)Level, X = X, Y = Y };

    public float MetresPerSample => PatchGrid.MetresPerSample(Level);

    /// <summary>Metres along one edge.</summary>
    public float Span => PatchGrid.Span(Level);

    /// <summary>World x of the patch's low corner, which is where its first sample sits.</summary>
    public float OriginX => X * Span;

    /// <summary>World y of the patch's low corner. Y runs north with world y, not down like a screen row.</summary>
    public float OriginY => Y * Span;

    public bool Equals(PatchKey other) => Level == other.Level && X == other.X && Y == other.Y;

    public override bool Equals(object obj) => obj is PatchKey other && Equals(other);

    public override int GetHashCode() => HashCode.Combine(Level, X, Y);

    /// <summary>Stable and filesystem-safe, because this is also the name a stored patch is kept under.</summary>
    public override string ToString() => $"L{Level}_{X}_{Y}";
  }

  /// <summary>
  /// How a detail level maps to a sample spacing and a footprint. Mirrors the server's <c>PatchGrid</c>.
  /// </summary>
  /// <remarks>
  /// The spacing doubles per level rather than quadrupling, which makes every level's samples a strict subset
  /// of the level below it. Two patches at different levels can then share an edge by agreeing on it instead
  /// of interpolating towards it, so the seam needs no stitching geometry at all.
  /// </remarks>
  public static class PatchGrid
  {
    /// <summary>Sample spacing at level 0, in metres. Four is an eighth of a chunk edge.</summary>
    public const float FinestMetres = 4.0f;

    /// <summary>Cells along a patch edge.</summary>
    public const int Cells = 64;

    /// <summary>Samples along a patch edge: <see cref="Cells"/> plus the far edge shared with the neighbour.</summary>
    public const int Samples = Cells + 1;

    /// <summary>Samples in a whole patch.</summary>
    public const int SampleCount = Samples * Samples;

    public const int MaxLevel = 3;

    public static float MetresPerSample(int level) => FinestMetres * (1 << level);

    public static float Span(int level) => Cells * MetresPerSample(level);

    public static int Index(int i, int j) => j * Samples + i;
  }
}
