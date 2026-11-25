using Godot;

namespace BestiaBehemothClient.Bnet.Message.Inn
{
  /// <summary>An inn's offer at its counter: the save point and the price of tending each bestia.</summary>
  [GlobalClass]
  public partial class InnSMSG : ISMSG
  {
    /// <summary>The innkeeper or the inn building; every request at this inn names it.</summary>
    [Export] public ulong CounterEntityId { get; set; }
    [Export] public string PlaceName { get; set; } = "";
    [Export] public bool SavePointHere { get; set; }
    [Export] public Godot.Collections.Array<InnTending> Tendings { get; set; } = [];

    public static InnSMSG FromProto(global::Bnet.InnSMSG proto)
    {
      var msg = new InnSMSG
      {
        CounterEntityId = proto.CounterEntityId,
        PlaceName = proto.PlaceName,
        SavePointHere = proto.SavePointHere,
      };
      foreach (var tending in proto.Tendings)
      {
        msg.Tendings.Add(InnTending.FromProto(tending));
      }

      return msg;
    }
  }
}
