using System.IO;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// Which square metres of one chunk column are burnt, and which are alight.
  /// </summary>
  /// <remarks>
  /// Arrives behind the chunk payload and is dropped with it, exactly as <see cref="ChunkStaticEntitiesSMSG"/>
  /// is. See the server-side proto for why what has happened to the ground travels beside the ground rather
  /// than in it: the chunk patch format can only ever *remove* a voxel, so no message in this protocol can
  /// change a voxel's material.
  ///
  /// <para>
  /// <b>Each message is the whole truth about its column, never a diff.</b> So applying one twice changes
  /// nothing, a lost one self-heals on the next send, and there is no sequence number to keep. A message with
  /// both masks absent means "this ground is clean now", which is how a healed scar retires.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class ChunkGroundOverlaySMSG : MapSMSG
  {
    /// <summary>The bitmask layout this build understands. Mirrors the proto enum's value, not its ordinal.</summary>
    private const uint BitmaskV1 = 1;

    public ChunkKey Key { get; private set; }

    /// <summary>Ground a fire has been through, or null when none of this column is burnt.</summary>
    public byte[] Scorched { get; private set; }

    /// <summary>Ground alight right now, or null when nothing here is burning - which is almost always.</summary>
    public byte[] Burning { get; private set; }

    /// <summary>
    /// Decodes one overlay, or throws if this build cannot read it.
    /// </summary>
    /// <remarks>
    /// Refuses an unknown encoding rather than guessing, for the reason the proto gives and which matters more
    /// here than for a patch: <b>any</b> byte string is a legal bitmask, so a decoder that disagrees about the
    /// layout draws a plausible pattern of scorch in the wrong places. That is indistinguishable from a fire
    /// having genuinely burnt there, so there is no symptom to notice.
    /// </remarks>
    /// <exception cref="InvalidDataException">
    /// on an encoding this build does not know, or a mask that is not the length <paramref name="chunkSize"/>
    /// implies. A short mask would read as a column whose tail is simply unburnt.
    /// </exception>
    public static ChunkGroundOverlaySMSG FromProto(global::Bnet.ChunkGroundOverlaySMSG proto, int chunkSize)
    {
      if ((uint)proto.Encoding != BitmaskV1)
      {
        throw new InvalidDataException(
          $"ground overlay encoding {proto.Encoding} is not one this build can read");
      }

      var expected = (chunkSize * chunkSize + 7) / 8;

      return new ChunkGroundOverlaySMSG
      {
        Key = new ChunkKey(proto.Pos.X, proto.Pos.Y, proto.Pos.Z),
        Scorched = MaskOrNull(proto.Scorched.ToByteArray(), expected, "scorched"),
        Burning = MaskOrNull(proto.Burning.ToByteArray(), expected, "burning")
      };
    }

    /// <summary>Null for an absent mask - proto3 does not encode an empty <c>bytes</c> - and never a short one.</summary>
    private static byte[] MaskOrNull(byte[] mask, int expected, string what)
    {
      if (mask == null || mask.Length == 0)
      {
        return null;
      }

      if (mask.Length != expected)
      {
        throw new InvalidDataException($"{what} mask is {mask.Length} B, expected {expected} B");
      }

      return mask;
    }

    /// <summary>Whether this column has nothing on it, which is the retire signal rather than a no-op.</summary>
    public bool IsClean => Scorched == null && Burning == null;

    /// <summary>
    /// Whether cell <c>(localX, localY)</c> is set in <paramref name="mask"/>.
    /// </summary>
    /// <remarks>
    /// <c>localY * chunkSize + localX</c>, that index's bit in byte <c>index / 8</c> counting from the least
    /// significant. This is the half of the wire contract that lives on this side, and
    /// <c>ColumnMaskTest</c> on the server pins the same layout from a hand-written fixture.
    /// </remarks>
    public static bool IsSet(byte[] mask, int chunkSize, int localX, int localY)
    {
      if (mask == null)
      {
        return false;
      }

      var index = localY * chunkSize + localX;

      return (mask[index >> 3] >> (index & 7) & 1) == 1;
    }

    public override string ToString() =>
      $"ChunkGroundOverlaySMSG({Key}) {Scorched?.Length ?? 0}B scorched, {Burning?.Length ?? 0}B burning";
  }
}
