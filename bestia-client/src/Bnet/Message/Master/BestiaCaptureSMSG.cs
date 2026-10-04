using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// A wild bestia sprang a trap and the catch was rolled.
  /// </summary>
  [GlobalClass]
  public partial class BestiaCaptureSMSG : ISMSG
  {
    [Export] public ulong TrapEntityId { get; set; }
    [Export] public ulong TargetEntityId { get; set; }
    [Export] public ulong TrapperEntityId { get; set; }
    [Export] public bool Success { get; set; }

    public static BestiaCaptureSMSG FromProto(global::Bnet.BestiaCaptureSMSG proto)
    {
      return new BestiaCaptureSMSG
      {
        TrapEntityId = proto.TrapEntityId,
        TargetEntityId = proto.TargetEntityId,
        TrapperEntityId = proto.TrapperEntityId,
        Success = proto.Success
      };
    }
  }
}
