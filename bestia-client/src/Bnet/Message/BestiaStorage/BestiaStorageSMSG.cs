using Godot;

namespace BestiaBehemothClient.Bnet.Message.BestiaStorage
{
  /// <summary>The selected master's bestia storage, sent on request and after every change to it.</summary>
  [GlobalClass]
  public partial class BestiaStorageSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<StoredBestia> Bestias { get; set; } = [];
    [Export] public int StorageSlotCount { get; set; }
    [Export] public int ActiveSlotCount { get; set; }
    [Export] public int ActiveBestiaCount { get; set; }

    public static BestiaStorageSMSG FromProto(global::Bnet.BestiaStorageSMSG proto)
    {
      var msg = new BestiaStorageSMSG
      {
        StorageSlotCount = (int)proto.StorageSlotCount,
        ActiveSlotCount = (int)proto.ActiveSlotCount,
        ActiveBestiaCount = (int)proto.ActiveBestiaCount,
      };
      foreach (var bestia in proto.Bestias)
      {
        msg.Bestias.Add(StoredBestia.FromProto(bestia));
      }

      return msg;
    }
  }
}
