using System.Collections.Generic;
using System.IO;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Decodes the packed edit list in a chunk patch. The C# half of the server's <c>ChunkPatchCodec</c>.
  /// </summary>
  /// <remarks>
  /// <code>
  /// repeated: uvar voxelIndex, u8 blockId, u8 occupancy
  /// </code>
  ///
  /// <para>
  /// The index is <c>(localY * size + localX) * height + localZ</c>, the same layout the chunk payload uses,
  /// so it can be applied to the decoded arrays directly with no coordinate arithmetic.
  /// </para>
  ///
  /// <para>
  /// Block and occupancy always travel together, even though occupancy is derivable from material everywhere
  /// but a surface. An edit format able to express one without the other is one that can break the
  /// air-implies-empty invariant in transit.
  /// </para>
  /// </remarks>
  public static class ChunkPatchCodec
  {
    public readonly struct Edit
    {
      public int Index { get; }
      public byte BlockId { get; }
      public byte Occupancy { get; }

      public Edit(int index, byte blockId, byte occupancy)
      {
        Index = index;
        BlockId = blockId;
        Occupancy = occupancy;
      }
    }

    public static List<Edit> Decode(byte[] bytes)
    {
      var edits = new List<Edit>();
      var at = 0;

      while (at < bytes.Length)
      {
        var index = 0;
        var shift = 0;

        while (true)
        {
          if (at >= bytes.Length)
          {
            throw new InvalidDataException($"Patch is truncated mid-index after {edits.Count} edits");
          }

          var b = bytes[at++];
          index |= (b & 0x7F) << shift;

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

        if (at + 2 > bytes.Length)
        {
          // Half an edit is not a smaller edit set. Applying the part that parsed would leave this chunk
          // silently disagreeing with the server's, which is the one outcome the revision check exists to
          // prevent - so refuse the patch and let the caller re-request the chunk.
          throw new InvalidDataException(
            $"Patch ends after the index of edit {edits.Count}, with no block and occupancy");
        }

        edits.Add(new Edit(index, bytes[at], bytes[at + 1]));
        at += 2;
      }

      return edits;
    }
  }
}
