using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  public partial class GetMasterCMSG : ICMSG
  {

    public override Envelope ToEnvelope()
    {
      var getMaster = new global::Bnet.GetMasterCMSG();

      return new Envelope
      {
        GetMaster = getMaster
      };
    }
  }
}