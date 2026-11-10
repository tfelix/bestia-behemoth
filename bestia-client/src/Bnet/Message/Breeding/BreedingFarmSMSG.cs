using Godot;

namespace BestiaBehemothClient.Bnet.Message.Breeding
{
  /// <summary>
  /// A Breeding Farm as its owner sees it. Sent when the owner clicks it and on every change while they are online.
  /// </summary>
  [GlobalClass]
  public partial class BreedingFarmSMSG : ISMSG
  {
    [Export] public ulong StructureId { get; set; }
    /// <summary>The <c>BreedingFarmPhase</c> ordinal.</summary>
    [Export] public int Phase { get; set; }

    /// <summary>0 when the farm has no pair.</summary>
    [Export] public ulong MotherEntityId { get; set; }
    [Export] public ulong FatherEntityId { get; set; }
    [Export] public bool HasBundle { get; set; }
    [Export] public ulong RemainingMs { get; set; }
    [Export] public ulong TotalMs { get; set; }

    /// <summary>Null until the egg is ready.</summary>
    [Export] public BestiaEggInfo Egg { get; set; }

    public static BreedingFarmSMSG FromProto(global::Bnet.BreedingFarmSMSG proto)
    {
      return new BreedingFarmSMSG
      {
        StructureId = proto.StructureId,
        Phase = (int)proto.Phase,
        MotherEntityId = proto.MotherEntityId,
        FatherEntityId = proto.FatherEntityId,
        HasBundle = proto.HasBundle,
        RemainingMs = proto.RemainingMs,
        TotalMs = proto.TotalMs,
        Egg = BestiaEggInfo.FromProto(proto.Egg)
      };
    }
  }
}
