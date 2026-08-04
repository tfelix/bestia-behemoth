using System.Collections.Generic;
using System.IO;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Decodes the packed removal list in a chunk patch. The C# half of the server's <c>ChunkPatchCodec</c>.
  /// </summary>
  /// <remarks>
  /// <code>
  /// repeated: uvar indexDelta, u8 remainingOccupancy
  /// </code>
  ///
  /// <para>
  /// Removals are sorted by voxel index, and each index arrives as the <b>gap since the previous one</b> rather
  /// than in full. That is what makes a removal cheap: an index is
  /// <c>(localY * size + localX) * height + localZ</c>, the same layout the chunk payload uses, so the vertical
  /// axis is contiguous and the voxels a brush takes out of one column are adjacent - nearly every gap is 1 and
  /// costs a single byte. The absolute index is still what comes out, so it applies to the decoded arrays
  /// directly with no coordinate arithmetic.
  /// </para>
  ///
  /// <para>
  /// <b>No block id.</b> There is no building system, so the only terrain mutation is removal - and then the
  /// material is derivable rather than absent: a voxel with anything left keeps what the generator gave it, and
  /// one carved to zero is air. This replaces a note that used to argue the pair must always travel together
  /// lest the air-implies-empty invariant break in transit. That was right for a format that could place
  /// arbitrary material; what is true now is stronger. The invariant is re-established independently on each
  /// side from data each side already holds, so a patch cannot express a violation of it at all. See
  /// <see cref="VoxelChunk.ApplyRemoval"/>, which is where this side re-establishes it.
  /// </para>
  /// </remarks>
  public static class ChunkPatchCodec
  {
    /// <summary>One voxel, and how much of it is left.</summary>
    public readonly struct Removal
    {
      public int Index { get; }

      /// <summary>Occupancy remaining, <c>0</c> meaning the voxel is now air.</summary>
      public byte RemainingOccupancy { get; }

      public Removal(int index, byte remainingOccupancy)
      {
        Index = index;
        RemainingOccupancy = remainingOccupancy;
      }
    }

    public static List<Removal> Decode(byte[] bytes)
    {
      var removals = new List<Removal>();
      var at = 0;
      var index = 0;

      while (at < bytes.Length)
      {
        var gap = 0;
        var shift = 0;

        while (true)
        {
          if (at >= bytes.Length)
          {
            throw new InvalidDataException($"Patch is truncated mid-index after {removals.Count} removals");
          }

          var b = bytes[at++];
          gap |= (b & 0x7F) << shift;

          if ((b & 0x80) == 0)
          {
            break;
          }

          shift += 7;

          if (shift >= 35)
          {
            throw new InvalidDataException("Varint in patch is longer than five bytes");
          }
        }

        if (at >= bytes.Length)
        {
          // Half a removal is not a smaller removal set. Applying the part that parsed would leave this chunk
          // silently disagreeing with the server's, which is the one outcome the revision check exists to
          // prevent - so refuse the patch and let the caller re-request the chunk.
          throw new InvalidDataException(
            $"Patch ends after the index of removal {removals.Count}, with no occupancy");
        }

        index += gap;
        removals.Add(new Removal(index, bytes[at++]));
      }

      return removals;
    }
  }
}
