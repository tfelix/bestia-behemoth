using System.IO;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// What has passed over one chunk column, with a shape and a heading.
  /// </summary>
  /// <remarks>
  /// Arrives behind the chunk payload and is dropped with it, as <see cref="ChunkGroundLayersSMSG"/> is. That
  /// message carries what the ground <i>is</i> - a grid of levels changing over days - and this carries
  /// discrete events arriving several times a second, which is why they do not share one.
  ///
  /// <para>
  /// <b>Each message is the whole truth about its column, never a diff.</b> Applying one twice changes nothing,
  /// a lost one self-heals on the next send, and an empty one is how faded tracks retire.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class ChunkGroundStampsSMSG : MapSMSG
  {
    /// <summary>The stamp layout this build understands. Mirrors the proto enum's value, not its ordinal.</summary>
    private const uint PackedV1 = 1;

    public ChunkKey Key { get; private set; }

    /// <summary>This column's stamps, oldest first. Empty retires whatever it had.</summary>
    public GroundStamp[] Stamps { get; private set; }

    /// <summary>
    /// Decodes one column's stamps, or throws if this build cannot read them.
    /// </summary>
    /// <remarks>
    /// Refuses an unknown encoding rather than guessing, for <see cref="ChunkGroundLayersSMSG"/>'s reason and
    /// with the same sharp edge: any byte string is a legal payload, so a decoder that disagrees about the
    /// layout draws tracks pointing the wrong way. Nobody reports that as a bug.
    /// </remarks>
    /// <exception cref="InvalidDataException">
    /// on an encoding this build does not know, or a payload that is not a whole number of stamps - which
    /// would otherwise read as a column whose last print simply went missing.
    /// </exception>
    public static ChunkGroundStampsSMSG FromProto(global::Bnet.ChunkGroundStampsSMSG proto)
    {
      if ((uint)proto.Encoding != PackedV1)
      {
        throw new InvalidDataException($"ground stamp encoding {proto.Encoding} is not one this build can read");
      }

      var payload = proto.Stamps.ToByteArray();
      if (payload.Length % GroundStampCells.BytesPerStamp != 0)
      {
        throw new InvalidDataException(
          $"ground stamps are {payload.Length} B, not a whole number of {GroundStampCells.BytesPerStamp} B stamps");
      }

      return new ChunkGroundStampsSMSG
      {
        Key = new ChunkKey(proto.Pos.X, proto.Pos.Y, proto.Pos.Z),
        Stamps = GroundStampCells.Decode(payload)
      };
    }

    /// <summary>Whether nothing has passed here, which is the retire signal rather than a no-op.</summary>
    public bool IsClean => Stamps.Length == 0;

    public override string ToString() => $"ChunkGroundStampsSMSG({Key}) {Stamps.Length} stamp(s)";
  }
}
