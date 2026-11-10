using Godot;

namespace BestiaBehemothClient.Bnet.Message.Breeding
{
  /// <summary>
  /// An Incubator as its owner sees it. Sent when the owner clicks it and on every change while they are online.
  /// </summary>
  [GlobalClass]
  public partial class IncubatorSMSG : ISMSG
  {
    [Export] public ulong StructureId { get; set; }

    /// <summary>Null while the incubator is empty.</summary>
    [Export] public BestiaEggInfo Egg { get; set; }

    /// <summary>Real time left at this incubator's speed.</summary>
    [Export] public ulong RemainingMs { get; set; }

    /// <summary>Ready, but the owner has no free bestia slot.</summary>
    [Export] public bool WaitingForSlot { get; set; }

    public static IncubatorSMSG FromProto(global::Bnet.IncubatorSMSG proto)
    {
      return new IncubatorSMSG
      {
        StructureId = proto.StructureId,
        Egg = BestiaEggInfo.FromProto(proto.Egg),
        RemainingMs = proto.RemainingMs,
        WaitingForSlot = proto.WaitingForSlot
      };
    }
  }
}
