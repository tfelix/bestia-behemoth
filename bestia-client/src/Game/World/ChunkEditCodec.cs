using System.Collections.Generic;
using System.IO;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Decodes the packed edit list in a chunk patch. The C# half of the server's <c>ChunkEditCodec</c>.
  /// </summary>
  /// <remarks>
  /// <code>
  /// repeated: uvar indexDelta, u8 blockId, u8 occupancy
  /// </code>
  ///
  /// <para>
  /// Edits are sorted by voxel index, and each index arrives as the <b>gap since the previous one</b> rather than
  /// in full. An index is <c>(localY * size + localX) * height + localZ</c>, the same layout the chunk payload
  /// uses, so the voxels of one column are adjacent and nearly every gap costs a single byte. The absolute index is
  /// what comes out, so it applies to the decoded arrays directly with no coordinate arithmetic.
  /// </para>
  /// </remarks>
  public static class ChunkEditCodec
  {
    /// <summary>One voxel's whole new state.</summary>
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
      var index = 0;

      while (at < bytes.Length)
      {
        var gap = 0;
        var shift = 0;

        while (true)
        {
          if (at >= bytes.Length)
          {
            throw new InvalidDataException($"Patch is truncated mid-index after {edits.Count} edits");
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

        if (at + 2 > bytes.Length)
        {
          // Half an edit is not a smaller edit set. Applying the part that parsed would leave this chunk silently
          // disagreeing with the server's, so refuse the patch and let the caller re-request the chunk.
          throw new InvalidDataException($"Patch ends after the index of edit {edits.Count}, with no voxel");
        }

        index += gap;
        edits.Add(new Edit(index, bytes[at], bytes[at + 1]));
        at += 2;
      }

      return edits;
    }
  }
}
