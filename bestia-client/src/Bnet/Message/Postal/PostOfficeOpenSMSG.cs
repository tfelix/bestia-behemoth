using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>The postmaster at work opens the post office window.</summary>
  [GlobalClass]
  public partial class PostOfficeOpenSMSG : ISMSG
  {
    [Export] public ulong PostmasterEntityId { get; set; }
    [Export] public string OfficeName { get; set; } = "";

    /// <summary>1 to 3. Sets how often the coach leaves.</summary>
    [Export] public int Level { get; set; }

    /// <summary>Where the master's parcels go. Empty when the master has none yet.</summary>
    [Export] public string HomeOfficeName { get; set; } = "";
    [Export] public bool IsHome { get; set; }

    public static PostOfficeOpenSMSG FromProto(global::Bnet.PostOfficeOpenSMSG proto)
    {
      return new PostOfficeOpenSMSG
      {
        PostmasterEntityId = proto.PostmasterEntityId,
        OfficeName = proto.OfficeName,
        Level = proto.Level,
        HomeOfficeName = proto.HomeOfficeName,
        IsHome = proto.IsHome
      };
    }
  }
}
