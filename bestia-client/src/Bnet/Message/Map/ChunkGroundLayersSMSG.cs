using System.IO;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// Everything lasting that has happened to the ground of one chunk column.
  /// </summary>
  /// <remarks>
  /// Arrives behind the chunk payload and is dropped with it, exactly as <see cref="ChunkStaticEntitiesSMSG"/>
  /// is. What is <i>happening</i> to the ground right now - fire - is <see cref="ChunkGroundOverlaySMSG"/>
  /// instead; the two move at completely different speeds and sharing a message would re-send every worn cell
  /// on every step of a burn.
  ///
  /// <para>
  /// <b>Every layer arrives together, deliberately.</b> The layers composite against a single remaining
  /// budget, so a half-updated set would spend a frame drawing blood with no path beneath it.
  /// </para>
  ///
  /// <para>
  /// <b>Each message is the whole truth about its column, never a diff.</b> Applying one twice changes
  /// nothing, a lost one self-heals on the next send, and an empty one is how a healed scar or a faded path
  /// retires.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class ChunkGroundLayersSMSG : MapSMSG
  {
    /// <summary>The cell layout this build understands. Mirrors the proto enum's value, not its ordinal.</summary>
    private const uint NibbleV1 = 1;

    public ChunkKey Key { get; private set; }

    /// <summary>
    /// Cells per channel, or null for a channel this column has nothing of - which is most of them, most of
    /// the time. Indexed by <see cref="GroundLayers.ChannelOf"/>, not by wire id.
    /// </summary>
    public byte[][] Cells { get; private set; }

    /// <summary>
    /// Decodes one column's layers, or throws if this build cannot read them.
    /// </summary>
    /// <remarks>
    /// Refuses an unknown encoding rather than guessing, for <see cref="ChunkGroundOverlaySMSG"/>'s reason and
    /// with the same sharp edge: any byte string is a legal payload, so a decoder that disagrees about the
    /// nibble order draws plausible wear in the wrong places. Nobody reports that as a bug.
    ///
    /// <para>
    /// An unknown <i>layer</i> is different and is skipped rather than refused: a newer server adding a fifth
    /// mark should cost this client that mark, not the whole column.
    /// </para>
    /// </remarks>
    /// <exception cref="InvalidDataException">
    /// on an encoding this build does not know, or cells that are not the length <paramref name="chunkSize"/>
    /// implies. A short payload would read as a column whose tail is simply unmarked.
    /// </exception>
    public static ChunkGroundLayersSMSG FromProto(global::Bnet.ChunkGroundLayersSMSG proto, int chunkSize)
    {
      var expected = GroundLayerCells.ByteLength(chunkSize);
      var cells = new byte[GroundLayers.Channels][];

      foreach (var layer in proto.Layers)
      {
        if ((uint)layer.Encoding != NibbleV1)
        {
          throw new InvalidDataException(
            $"ground layer encoding {layer.Encoding} is not one this build can read");
        }

        var channel = GroundLayers.ChannelOf((GroundLayers.Id)layer.Layer);
        if (channel < 0)
        {
          continue;
        }

        var payload = layer.Cells.ToByteArray();
        if (payload.Length != expected)
        {
          throw new InvalidDataException(
            $"{layer.Layer} cells are {payload.Length} B, expected {expected} B");
        }

        cells[channel] = payload;
      }

      return new ChunkGroundLayersSMSG
      {
        Key = new ChunkKey(proto.Pos.X, proto.Pos.Y, proto.Pos.Z),
        Cells = cells
      };
    }

    /// <summary>Whether this column has nothing on it, which is the retire signal rather than a no-op.</summary>
    public bool IsClean
    {
      get
      {
        foreach (var channel in Cells)
        {
          if (channel != null)
          {
            return false;
          }
        }

        return true;
      }
    }

    public override string ToString()
    {
      var bytes = 0;
      var layers = 0;
      foreach (var channel in Cells)
      {
        if (channel == null)
        {
          continue;
        }

        layers++;
        bytes += channel.Length;
      }

      return $"ChunkGroundLayersSMSG({Key}) {layers} layer(s), {bytes}B";
    }
  }
}
