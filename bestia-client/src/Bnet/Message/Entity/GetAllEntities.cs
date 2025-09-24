using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Message for requesting all entities and map data in range.
  /// Engine sends this e.g. after a load or if it thinks it needs to re-sync the whole environment.
  /// </summary>
  public partial class GetAllEntities : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      var getAllEntities = new global::Bnet.GetAllEntities();

      return new Envelope
      {
        GetAllEntities = getAllEntities
      };
    }
  }
}