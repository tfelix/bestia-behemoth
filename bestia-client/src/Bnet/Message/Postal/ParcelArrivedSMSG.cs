using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>A pigeon says a parcel waits for the played master.</summary>
  [GlobalClass]
  public partial class ParcelArrivedSMSG : ISMSG
  {
    [Export] public string SenderName { get; set; } = "";
    [Export] public string OfficeName { get; set; } = "";

    public static ParcelArrivedSMSG FromProto(global::Bnet.ParcelArrivedSMSG proto)
    {
      return new ParcelArrivedSMSG { SenderName = proto.SenderName, OfficeName = proto.OfficeName };
    }
  }
}
