using Godot;

namespace BestiaBehemothClient.Bnet.Message.Breeding
{
  /// <summary>What an egg in the master's bag holds. The answer to an <see cref="InspectEggCMSG"/>.</summary>
  [GlobalClass]
  public partial class BestiaEggSMSG : ISMSG
  {
    [Export] public BestiaEggInfo Egg { get; set; }

    public static BestiaEggSMSG FromProto(global::Bnet.BestiaEggSMSG proto)
    {
      return new BestiaEggSMSG { Egg = BestiaEggInfo.FromProto(proto.Egg) };
    }
  }
}
