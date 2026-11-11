using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>Names a new work area around where one of the player's bestias stands.</summary>
  public partial class SaveWorkAreaCMSG : ICMSG
  {
    public ulong PlayerBestiaId { get; set; }
    public string Name { get; set; } = "";
    public uint Radius { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SaveWorkArea = new global::Bnet.SaveWorkAreaCMSG { PlayerBestiaId = PlayerBestiaId, Name = Name, Radius = Radius }
      };
    }
  }
}
