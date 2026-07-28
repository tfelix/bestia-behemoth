using System;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// A chunk address, and the dictionary key the client stores chunks under.
  /// </summary>
  /// <remarks>
  /// Three signed ints, because <c>Z</c> is routinely negative: voxel index zero is sea level rather than a
  /// world floor, so everything below the waterline has a negative vertical index. The server's own folded
  /// <c>ChunkPos.key()</c> masks each axis to 21 unsigned bits and cannot express that, which is why it stays
  /// a cache key on that side and never appears on the wire.
  /// </remarks>
  public readonly struct ChunkKey : IEquatable<ChunkKey>
  {
    public int X { get; }
    public int Y { get; }
    public int Z { get; }

    public ChunkKey(int x, int y, int z)
    {
      X = x;
      Y = y;
      Z = z;
    }

    public static ChunkKey FromProto(global::Bnet.ChunkPos pos) => new(pos.X, pos.Y, pos.Z);

    public global::Bnet.ChunkPos ToProto() => new() { X = X, Y = Y, Z = Z };

    public bool Equals(ChunkKey other) => X == other.X && Y == other.Y && Z == other.Z;

    public override bool Equals(object obj) => obj is ChunkKey other && Equals(other);

    public override int GetHashCode() => HashCode.Combine(X, Y, Z);

    public override string ToString() => $"{X},{Y},{Z}";
  }
}
