using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// Add a craft to a station's queue; the ingredients for the whole amount move into the station at once. The
  /// answer is a <see cref="StationStateSMSG"/>, or an <c>OperationError</c>.
  /// </summary>
  public partial class QueueCraftCMSG : ICMSG
  {
    public ulong StructureId { get; set; }
    public ulong RecipeId { get; set; }
    public uint Amount { get; set; } = 1;

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        QueueCraft = new global::Bnet.QueueCraftCMSG { StructureId = StructureId, RecipeId = RecipeId, Amount = Amount }
      };
    }
  }
}
