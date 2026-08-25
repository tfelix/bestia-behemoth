using System.IO;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// One coarse patch of ground: the visible surface, without the voxels under it.
  /// </summary>
  /// <remarks>
  /// Around two kilobytes for what sixty-four chunks would cost nearly two hundred. That ratio is the whole
  /// reason the draw distance can reach past the chunk radius at all.
  ///
  /// <para>
  /// Decoding is deferred to <see cref="Decode"/> for <see cref="ChunkDataSMSG"/>'s reason: conversion happens
  /// while draining the receive queue, so a login's worth of patches inflated there would all land in one
  /// frame. <c>ChunkStreamManager</c> spreads the work instead.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class SurfacePatchSMSG : MapSMSG
  {
    public PatchKey Key { get; private init; }

    /// <summary>Whether <see cref="Payload"/> is deflated. Always true in practice; a patch always compresses.</summary>
    [Export] public bool Compressed { get; set; }

    /// <summary>Bytes as received, before inflation, so <see cref="Decode"/> can be called off-frame.</summary>
    // global:: because there is a `Bnet.Message.System` namespace, which otherwise wins over `System`.
    public byte[] Payload { get; private init; } = global::System.Array.Empty<byte>();

    public static SurfacePatchSMSG FromProto(global::Bnet.SurfacePatchSMSG proto)
    {
      if (proto.Encoding != global::Bnet.SurfacePatchEncoding.PlanesV1)
      {
        // Refusing beats guessing, exactly as it does for a chunk: a payload whose encoding this build does
        // not know decodes into plausible terrain rather than failing, which is the worse outcome.
        throw new InvalidDataException(
          $"Surface patch ({proto.Pos.Level},{proto.Pos.X},{proto.Pos.Y}) uses encoding {proto.Encoding}, " +
          "which this build cannot read");
      }

      return new SurfacePatchSMSG
      {
        Key = PatchKey.FromProto(proto.Pos),
        Compressed = proto.Compression == global::Bnet.ChunkCompression.Deflate,
        Payload = proto.Payload.ToByteArray()
      };
    }

    /// <summary>
    /// A payload read back from <see cref="PatchDiskCache"/>, which stores it exactly as it arrived.
    /// </summary>
    /// <remarks>
    /// A factory rather than an object initialiser because <see cref="Payload"/> is <c>init</c>-only, and it
    /// is init-only so that nothing can swap the bytes out from under a queued message.
    /// </remarks>
    public static SurfacePatchSMSG FromCache(PatchKey key, byte[] payload) => new()
    {
      Key = key,
      Compressed = true,
      Payload = payload
    };

    /// <summary>Inflates if needed and reads the planes. Throws on anything malformed.</summary>
    public SurfacePatch Decode() =>
      SurfacePatchCodec.Decode(Compressed ? RleCodec.Inflate(Payload) : Payload);

    public override string ToString() => $"SurfacePatch[{Key}, {Payload.Length} B]";
  }
}
