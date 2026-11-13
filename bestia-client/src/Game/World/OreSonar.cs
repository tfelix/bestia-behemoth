using System;
using System.Collections.Generic;
using BestiaBehemothClient.Game.World.Mesh;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Resource Sonar's eye: the ore and gem blocks within a radius, read from the chunks the client already holds.
  /// </summary>
  /// <remarks>
  /// The skill is passive and the server sends nothing for it. Every block is in the client's hands already, so
  /// the sonar only chooses which of them to light up through the rock.
  /// </remarks>
  public static class OreSonar
  {
    /// <summary>One ore block, in server voxel coordinates.</summary>
    public readonly record struct Find(long X, long Y, int Z, int Block);

    /// <summary>Every ore block within <paramref name="radius"/> voxels of the centre, nearest first.</summary>
    public static List<Find> Around(
      IChunkSource source, ChunkWrap wrap, long centreX, long centreY, int centreZ, int radius,
      Func<int, bool> isOre, int chunkSize, int chunkHeight)
    {
      var found = new List<Find>();
      var radiusSquared = (long)radius * radius;

      for (var dz = -radius; dz <= radius; dz++)
      for (var dy = -radius; dy <= radius; dy++)
      for (var dx = -radius; dx <= radius; dx++)
      {
        if ((long)dx * dx + (long)dy * dy + (long)dz * dz > radiusSquared)
        {
          continue;
        }

        var block = BlockAt(source, wrap, centreX + dx, centreY + dy, centreZ + dz, chunkSize, chunkHeight);
        if (block >= 0 && isOre(block))
        {
          found.Add(new Find(centreX + dx, centreY + dy, centreZ + dz, block));
        }
      }

      found.Sort((a, b) => DistanceSquared(a, centreX, centreY, centreZ)
        .CompareTo(DistanceSquared(b, centreX, centreY, centreZ)));

      return found;
    }

    /// <summary>Whether <paramref name="name"/>, a palette name, is an ore or a gem the sonar shows.</summary>
    public static bool IsOreName(string name) => name.StartsWith("ORE_") || name.StartsWith("GEM_");

    private static int BlockAt(IChunkSource source, ChunkWrap wrap, long x, long y, int z, int chunkSize, int chunkHeight)
    {
      var chunkX = (int)Math.Floor((double)x / chunkSize);
      var chunkY = (int)Math.Floor((double)y / chunkSize);
      var chunkZ = (int)Math.Floor((double)z / chunkHeight);
      var chunk = source.Get(wrap.Normalise(new ChunkKey(chunkX, chunkY, chunkZ)));
      if (chunk == null)
      {
        return -1;
      }

      return chunk.BlockAt(
        (int)(x - (long)chunkX * chunkSize), (int)(y - (long)chunkY * chunkSize), z - chunkZ * chunkHeight);
    }

    private static long DistanceSquared(Find find, long x, long y, int z)
    {
      var dx = find.X - x;
      var dy = find.Y - y;
      var dz = (long)find.Z - z;

      return dx * dx + dy * dy + dz * dz;
    }
  }
}
