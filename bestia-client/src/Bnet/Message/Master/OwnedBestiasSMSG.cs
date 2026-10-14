using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Every bestia the selected master owns, sent whole whenever that changes.
  /// </summary>
  [GlobalClass]
  public partial class OwnedBestiasSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<BestiaInfo> Bestias { get; set; } = [];
    [Export] public int BestiaSlotCount { get; set; }

    public static OwnedBestiasSMSG FromProto(global::Bnet.OwnedBestiasSMSG proto)
    {
      var msg = new OwnedBestiasSMSG { BestiaSlotCount = (int)proto.BestiaSlotCount };
      foreach (var bestia in proto.Bestias)
      {
        msg.Bestias.Add(BestiaInfo.FromProto(bestia));
      }

      return msg;
    }
  }
}
