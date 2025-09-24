using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Godot-friendly wrapper for SelfSMSG protobuf data containing information about the currently selected master and available bestias
  /// </summary>
  [GlobalClass]
  public partial class SelfSMSG : ISMSG
  {
    [Export]
    public ulong MasterId { get; set; }

    [Export]
    public ulong MasterEntityId { get; set; }

    [Export]
    public Godot.Collections.Array<BestiaInfo> AvailableBestias { get; set; } = [];

    /// <summary>
    /// Creates a SelfSMSG message from protobuf data
    /// </summary>
    /// <param name="protoSelf">The protobuf SelfSMSG object</param>
    /// <returns>Godot-friendly SelfSMSG object</returns>
    public static SelfSMSG FromProto(global::Bnet.SelfSMSG protoSelf)
    {
      var selfSmsg = new SelfSMSG
      {
        MasterId = protoSelf.MasterId,
        MasterEntityId = protoSelf.MasterEntityId
      };

      foreach (var bestia in protoSelf.AvailableBestias)
      {
        selfSmsg.AvailableBestias.Add(BestiaInfo.FromProto(bestia));
      }

      return selfSmsg;
    }
  }
}
