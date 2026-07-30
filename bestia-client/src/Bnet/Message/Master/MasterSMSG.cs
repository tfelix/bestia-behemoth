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
    /// Settlement spawn point candidates a new master can choose to start life near. The client is
    /// expected to pre-select one of these at random so a player who does not care can just hit Create.
    /// </summary>
    [Export]
    public Godot.Collections.Array<MasterSpawnPointCandidate> SpawnPoints { get; set; } = [];

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

      foreach (var protoCandidate in protoMaster.SpawnPoints)
      {
        master.SpawnPoints.Add(MasterSpawnPointCandidate.FromProto(protoCandidate));
      }

      return master;
    }
  }
}