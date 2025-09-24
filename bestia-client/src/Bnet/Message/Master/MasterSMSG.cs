using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Godot-friendly wrapper for Master protobuf data containing available masters and server slots
  /// </summary>
  [GlobalClass]
  public partial class MasterSMSG : ISMSG
  {
    [Export]
    public uint MaxAvailableMasterSlots { get; set; }

    [Export]
    public uint MaxAvailableBestiaSlots { get; set; }

    [Export]
    public Godot.Collections.Array<MasterInfo> Masters { get; set; } = [];

    /// <summary>
    /// Creates a Master message from protobuf data
    /// </summary>
    /// <param name="protoMaster">The protobuf Master object</param>
    /// <returns>Godot-friendly Master object</returns>
    public static MasterSMSG FromProto(global::Bnet.Master protoMaster)
    {
      var master = new MasterSMSG
      {
        MaxAvailableMasterSlots = protoMaster.MaxAvailableMasterSlots,
        MaxAvailableBestiaSlots = protoMaster.MaxAvailableBestiaSlots
      };

      // Convert protobuf MasterInfo list to Godot array
      foreach (var protoMasterInfo in protoMaster.Master_)
      {
        master.Masters.Add(MasterInfo.FromProto(protoMasterInfo));
      }

      return master;
    }
  }
}