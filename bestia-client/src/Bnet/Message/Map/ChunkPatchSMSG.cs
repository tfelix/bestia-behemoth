using System;
using System.Collections.Generic;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// The voxels that changed in one chunk, rather than the chunk that contains them.
  /// </summary>
  /// <remarks>
  /// A ten-voxel edit is some fifty bytes against three kilobytes for the chunk, which is what makes a
  /// crowded building site affordable: thirty players in range cost thirty copies of the patch, not thirty
  /// copies of the chunk.
  ///
  /// <para>
  /// <see cref="FromRevision"/> is an assertion rather than a mechanism - one connection is one ordered
  /// stream, and the server always sends a snapshot before any patch built on it. If it does not match what
  /// is held, something has gone wrong and the right response is to discard the chunk and ask for it again,
  /// never to apply the edits to the wrong base.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class ChunkPatchSMSG : ISMSG
  {
    public ChunkKey Key { get; private init; }

    [Export] public uint FromRevision { get; set; }
    [Export] public uint ToRevision { get; set; }

    public byte[] Edits { get; private init; } = Array.Empty<byte>();

    public static ChunkPatchSMSG FromProto(global::Bnet.ChunkPatchSMSG proto)
    {
      return new ChunkPatchSMSG
      {
        Key = ChunkKey.FromProto(proto.Pos),
        FromRevision = proto.FromRevision,
        ToRevision = proto.ToRevision,
        Edits = proto.Edits.ToByteArray()
      };
    }

    public List<ChunkPatchCodec.Edit> Decode() => ChunkPatchCodec.Decode(Edits);

    public override string ToString() =>
      $"ChunkPatch[{Key} rev {FromRevision}->{ToRevision}, {Edits.Length} B]";
  }
}
