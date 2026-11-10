using BestiaBehemothClient.Bnet.Message.Master;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Breeding
{
  /// <summary>An egg as its holder may read it. <see cref="UniqueId"/> is the egg item's instance id.</summary>
  [GlobalClass]
  public partial class BestiaEggInfo : GodotObject
  {
    [Export] public ulong UniqueId { get; set; }
    [Export] public uint MobId { get; set; }

    /// <summary>The <c>BestiaSex</c> ordinal: 0 unknown, 1 male, 2 female.</summary>
    [Export] public int Sex { get; set; }

    [Export] public IvReadingInfo Ivs { get; set; } = new();
    [Export] public ulong HatchTotalMs { get; set; }
    [Export] public ulong HatchDoneMs { get; set; }

    /// <summary>Null when the message carries no egg.</summary>
    public static BestiaEggInfo FromProto(global::Bnet.BestiaEgg proto)
    {
      if (proto == null)
      {
        return null;
      }

      return new BestiaEggInfo
      {
        UniqueId = proto.UniqueId,
        MobId = proto.MobId,
        Sex = (int)proto.Sex,
        Ivs = IvReadingInfo.FromProto(proto.Ivs),
        HatchTotalMs = proto.HatchTotalMs,
        HatchDoneMs = proto.HatchDoneMs
      };
    }
  }
}
