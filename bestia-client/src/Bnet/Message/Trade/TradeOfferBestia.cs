using BestiaBehemothClient.Bnet.Message.Master;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Trade
{
  /// <summary>A party bestia in one side of a trade. Its IVs come as grades only.</summary>
  [GlobalClass]
  public partial class TradeOfferBestia : GodotObject
  {
    [Export] public ulong PlayerBestiaId { get; set; }

    [Export] public ulong EntityId { get; set; }

    [Export] public uint MobId { get; set; }

    /// <summary>Empty for its species' name.</summary>
    [Export] public string Name { get; set; } = "";

    [Export] public uint Level { get; set; }

    /// <summary>The <c>BestiaSex</c> ordinal: 0 unknown, 1 male, 2 female.</summary>
    [Export] public int Sex { get; set; }

    /// <summary>The <c>BestiaOrigin</c> ordinal: 1 caught, 2 bred, 3 granted.</summary>
    [Export] public int Origin { get; set; }

    [Export] public string OriginMasterName { get; set; } = "";

    [Export] public IvReadingInfo Ivs { get; set; } = new();

    /// <summary>Items in its bag, worn ones included, that go with it.</summary>
    [Export] public uint CarriedItemCount { get; set; }

    [Export] public bool WearsGear { get; set; }

    public static TradeOfferBestia FromProto(global::Bnet.TradeOfferBestia proto)
    {
      return new TradeOfferBestia
      {
        PlayerBestiaId = proto.PlayerBestiaId,
        EntityId = proto.EntityId,
        MobId = proto.MobId,
        Name = proto.Name,
        Level = proto.Level,
        Sex = (int)proto.Sex,
        Origin = (int)proto.Origin,
        OriginMasterName = proto.OriginMasterName,
        Ivs = IvReadingInfo.FromProto(proto.Ivs),
        CarriedItemCount = proto.CarriedItemCount,
        WearsGear = proto.WearsGear
      };
    }
  }
}
