using System.Collections.Generic;
using System.IO;
using BestiaBehemothClient.Game.World;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// What lasting has happened to the ground of one chunk column, for the layers the message names.
  /// </summary>
  /// <remarks>
  /// Arrives behind the chunk payload and is dropped with it, exactly as <see cref="ChunkStaticEntitiesSMSG"/>
  /// is. What is <i>happening</i> to the ground right now - fire - is <see cref="ChunkGroundOverlaySMSG"/>
  /// instead; the two move at completely different speeds and sharing a message would re-send every worn cell
  /// on every step of a burn.
  ///
  /// <para>
  /// <b>Each layer is replaced on its own.</b> A message names the layers it speaks about; a layer it does not
  /// name is untouched, and one it names with no cells is retired. Each entry is the whole layer for the column,
  /// never a diff, so applying one twice changes nothing and a lost one self-heals on the next send.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class ChunkGroundLayersSMSG : MapSMSG
  {
    public ChunkKey Key { get; private set; }

    /// <summary>The layers this message speaks about, in the order it listed them.</summary>
    public IReadOnlyList<GroundLayerEntry> Layers { get; private set; }

    /// <summary>
    /// Decodes one column's layers, or throws if this build cannot read them.
    /// </summary>
    /// <remarks>
    /// Refuses an unknown encoding rather than guessing, for <see cref="ChunkGroundOverlaySMSG"/>'s reason and
    /// with the same sharp edge: any byte string is a legal payload, so a decoder that disagrees about the
    /// layout draws plausible wear in the wrong places. Nobody reports that as a bug. The same goes for an
    /// encoding on the wrong layer: worn ground and blood need their exact levels, and a nibble payload would
    /// seed them from a lossy copy.
    ///
    /// <para>
    /// An unknown <i>layer</i> is different and is skipped rather than refused: a newer server adding a fifth
    /// mark should cost this client that mark, not the whole column.
    /// </para>
    /// </remarks>
    /// <exception cref="InvalidDataException">
    /// on an encoding this build does not know or that does not suit its layer, or cells that are not the length
    /// <paramref name="chunkSize"/> implies. A short payload would read as a column whose tail is simply unmarked.
    /// </exception>
    public static ChunkGroundLayersSMSG FromProto(global::Bnet.ChunkGroundLayersSMSG proto, int chunkSize)
    {
      var layers = new List<GroundLayerEntry>();

      foreach (var layer in proto.Layers)
      {
        var id = (GroundLayers.Id)layer.Layer;
        if (GroundLayers.ChannelOf(id) < 0)
        {
          continue;
        }

        layers.Add(GroundLayerCells.ReadEntry(
          id, (uint)layer.Encoding, layer.Cells.ToByteArray(), layer.LastDecayedSecond, chunkSize));
      }

      return new ChunkGroundLayersSMSG
      {
        Key = new ChunkKey(proto.Pos.X, proto.Pos.Y, proto.Pos.Z),
        Layers = layers
      };
    }

    public override string ToString() => $"ChunkGroundLayersSMSG({Key}) {Layers.Count} layer(s)";
  }
}
