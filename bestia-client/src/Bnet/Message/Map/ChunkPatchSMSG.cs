using System;
using System.Collections.Generic;
using System.IO;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// The voxels a player removed from one chunk, rather than the chunk that contains them.
  /// </summary>
  /// <remarks>
  /// A swing's worth of mining is a couple of hundred bytes against three kilobytes for the chunk, which is
  /// what makes a crowded dig affordable: thirty players in range cost thirty copies of the patch, not thirty
  /// copies of the chunk.
  ///
  /// <para>
  /// <see cref="FromRevision"/> is an assertion rather than a mechanism - one connection is one ordered
  /// stream, and the server always sends a snapshot before any patch built on it. If it does not match what
  /// is held, something has gone wrong and the right response is to discard the chunk and ask for it again,
  /// never to apply the removals to the wrong base.
  /// </para>
  ///
  /// <para>
  /// <see cref="Encoding"/> is checked and not merely carried, and it is the only thing standing between a
  /// version skew and silent corruption. Every byte of a packed removal stream is a legal varint
  /// continuation, so a decoder reading the wrong format does not fail - it produces plausible geometry. The
  /// revision check cannot catch that, because the revision really did advance, and <c>base_hash</c> is only
  /// carried on a snapshot and never re-verified on a patch.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class ChunkPatchSMSG : MapSMSG
  {
    public ChunkKey Key { get; private init; }

    [Export] public uint FromRevision { get; set; }
    [Export] public uint ToRevision { get; set; }

    /// <summary>How many voxels this patch describes, as the server counted them.</summary>
    /// <remarks>
    /// Carried rather than derived from <see cref="Removals"/>'s length: a removal is one to four bytes now
    /// that indices are delta coded, so there is no division that recovers it.
    /// </remarks>
    [Export] public uint RemovalCount { get; set; }

    public global::Bnet.ChunkPatchEncoding Encoding { get; private init; }

    public byte[] Removals { get; private init; } = Array.Empty<byte>();

    public static ChunkPatchSMSG FromProto(global::Bnet.ChunkPatchSMSG proto)
    {
      return new ChunkPatchSMSG
      {
        Key = ChunkKey.FromProto(proto.Pos),
        FromRevision = proto.FromRevision,
        ToRevision = proto.ToRevision,
        RemovalCount = proto.RemovalCount,
        Encoding = proto.Encoding,
        Removals = proto.Removals.ToByteArray()
      };
    }

    /// <summary>
    /// The removals, or a throw if this build does not know the format they are in.
    /// </summary>
    /// <exception cref="InvalidDataException">
    /// The patch names an encoding this build cannot read. Refusing is the whole reason the field exists -
    /// guessing would decode to plausible garbage and put this client quietly out of step with the server.
    /// </exception>
    public List<ChunkPatchCodec.Removal> Decode()
    {
      if (Encoding != global::Bnet.ChunkPatchEncoding.RemovalV1)
      {
        throw new InvalidDataException(
          $"Chunk patch for {Key} is encoded as {Encoding}, which this build cannot read");
      }

      return ChunkPatchCodec.Decode(Removals);
    }

    public override string ToString() =>
      $"ChunkPatch[{Key} rev {FromRevision}->{ToRevision}, {RemovalCount} removals, {Removals.Length} B]";
  }
}
