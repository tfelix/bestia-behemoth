using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// A message about this player's party. A marker, like <see cref="MapSMSG"/>, so <c>ConnectionManager</c>
  /// forwards the whole family with one <c>is</c> test. <c>[GlobalClass]</c> so GDScript can name it.
  /// </summary>
  [GlobalClass]
  public abstract partial class PartySMSG : ISMSG
  {
  }
}
