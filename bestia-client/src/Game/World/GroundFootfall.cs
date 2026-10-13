using System;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The heading and shape of the print a footfall leaves, derived the way the server derives them.
  /// </summary>
  /// <remarks>
  /// Ports of <c>MarkingGroundTrample.octantOf</c> and <c>seedOf</c>, pinned by <c>GroundFootfallTest</c>. A
  /// local print and the server's are matched by (cell, octant, seed), so a disagreement shows as every print
  /// drawn twice. In C# because GDScript's <c>&gt;&gt;</c> is arithmetic where the server's <c>ushr</c> is logical.
  /// </remarks>
  public static class GroundFootfall
  {
    /// <summary>Multiplier of the server's 64-bit avalanche step.</summary>
    private const long Mix = -7046029254386353131L;

    private const int Octants = 8;

    /// <summary>
    /// Which of the eight directions a step went: 0 towards +x, counting towards +y.
    /// </summary>
    /// <remarks>
    /// <c>Math.Floor(x + 0.5)</c> is Kotlin's <c>Math.round</c>; .NET's <c>Math.Round</c> would round half to even.
    /// </remarks>
    public static int OctantOf(long dx, long dy)
    {
      if (dx == 0 && dy == 0)
      {
        return 0;
      }

      var eighths = (long)Math.Floor(Math.Atan2(dy, dx) / (Math.PI / 4) + 0.5);

      return (int)(((eighths % Octants) + Octants) % Octants);
    }

    /// <summary>
    /// A shape variant for one print, stable for as long as it exists.
    /// </summary>
    /// <remarks>
    /// Wrapping 64-bit arithmetic and a logical shift, as on the server. Returns 0..255.
    /// </remarks>
    public static int SeedOf(long entityId, long voxelX, long voxelY)
    {
      unchecked
      {
        var hash = entityId * 31 + voxelX;
        hash = hash * 31 + voxelY;
        hash ^= (long)((ulong)hash >> 27);
        hash *= Mix;
        hash ^= (long)((ulong)hash >> 31);

        return (int)(hash & 0xFF);
      }
    }
  }
}
