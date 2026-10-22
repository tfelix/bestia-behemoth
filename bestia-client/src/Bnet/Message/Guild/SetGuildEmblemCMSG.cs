using Bnet;
using Google.Protobuf;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Replaces the guild's emblem with a 32x32 PNG; see <c>EmblemPicture</c>.</summary>
  public partial class SetGuildEmblemCMSG : ICMSG
  {
    public byte[] Png { get; set; } = [];

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SetGuildEmblem = new global::Bnet.SetGuildEmblemCMSG
        {
          Png = ByteString.CopyFrom(Png)
        }
      };
    }
  }
}
