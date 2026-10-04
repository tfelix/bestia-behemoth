using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Asks to take control of another owned entity - a bestia, or the master again. The client switches only
  /// once <see cref="Master.ActiveEntitySMSG"/> confirms it.
  /// </summary>
  public partial class SelectActiveEntityCMSG : ICMSG
  {
    [Export] public ulong EntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SelectActiveEntity = new global::Bnet.SelectActiveEntity
        {
          EntityId = EntityId
        }
      };
    }
  }
}
