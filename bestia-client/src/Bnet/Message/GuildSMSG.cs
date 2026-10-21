using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// A message about this player's guild. A marker, like <see cref="PartySMSG"/>, so <c>ConnectionManager</c>
  /// forwards the whole family with one <c>is</c> test. <c>[GlobalClass]</c> so GDScript can name it.
  /// </summary>
  [GlobalClass]
  public abstract partial class GuildSMSG : ISMSG
  {
  }
}
