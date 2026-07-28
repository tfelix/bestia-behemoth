using System.IO;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// A whole chunk: the merged authoritative voxels, encoded and possibly compressed.
  /// </summary>
  /// <remarks>
  /// "Merged" means the generated terrain with every player edit already applied. The server has no way to
  /// hand out a base without its delta, so this is the same geometry it answers line of sight and movement
  /// validation from - this client is never shown a world the server does not believe in.
  ///
  /// <para>
  /// Decoding is deferred to <see cref="Decode"/> rather than done in <see cref="FromProto"/>. Message
  /// conversion happens while draining the receive queue, and a burst of chunks decoded there would all land
  /// in one frame; the caller spreads the work instead.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class ChunkDataSMSG : ISMSG
  {
    public ChunkKey Key { get; private init; }

    [Export] public uint Revision { get; set; }

    /// <summary>Base voxel hash, for a client that generates its own terrain. This one does not.</summary>
    [Export] public ulong BaseHash { get; set; }

    [Export] public bool Deflated { get; set; }

    /// <summary>Bytes as received, before inflation. Kept so <see cref="Decode"/> can be called off-frame.</summary>
    // global:: because there is a `Bnet.Message.System` namespace, which otherwise wins over `System`.
    public byte[] Payload { get; private init; } = global::System.Array.Empty<byte>();

    public int PayloadBytes => Payload.Length;

    public static ChunkDataSMSG FromProto(global::Bnet.ChunkDataSMSG proto)
    {
      if (proto.Encoding != global::Bnet.ChunkEncoding.RleV2)
      {
        // Refusing beats guessing: a payload whose encoding this build does not know cannot be decoded, and
        // the encoding field exists precisely so that is a clear failure rather than a corrupt chunk.
        throw new InvalidDataException(
          $"Chunk ({proto.Pos.X},{proto.Pos.Y},{proto.Pos.Z}) uses encoding {proto.Encoding}, " +
          $"which this build cannot read");
      }

      return new ChunkDataSMSG
      {
        Key = ChunkKey.FromProto(proto.Pos),
        Revision = proto.Revision,
        BaseHash = proto.BaseHash,
        Deflated = proto.Compression == global::Bnet.ChunkCompression.Deflate,
        Payload = proto.Payload.ToByteArray()
      };
    }

    /// <summary>Inflates if needed and decodes. Throws on anything malformed; see <see cref="RleCodec"/>.</summary>
    public VoxelChunk Decode()
    {
      var encoded = Deflated ? RleCodec.Inflate(Payload) : Payload;

      return RleCodec.Decode(Key.X, Key.Y, Key.Z, encoded);
    }

    /// <summary>Size of the encoded payload after inflation, for reporting the compression ratio.</summary>
    public int EncodedBytes() => Deflated ? RleCodec.Inflate(Payload).Length : Payload.Length;

    public override string ToString() =>
      $"ChunkData[{Key} rev {Revision}, {PayloadBytes} B{(Deflated ? " deflated" : "")}]";
  }
}
