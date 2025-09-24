using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Message for selecting a master to play with on the server
  /// </summary>
  public partial class SelectMasterCMSG : ICMSG
  {
    [Export]
    public ulong MasterId { get; set; } = 0;

    public override Envelope ToEnvelope()
    {
      var selectMaster = new global::Bnet.SelectMasterCMSG
      {
        MasterId = MasterId
      };

      return new Envelope
      {
        SelectMaster = selectMaster
      };
    }
  }
}