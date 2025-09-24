using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  [GlobalClass]
  public abstract partial class EntitySMSG : ISMSG
  {
    [Export]
    public ulong EntityId { get; set; } = 0;
  }
}