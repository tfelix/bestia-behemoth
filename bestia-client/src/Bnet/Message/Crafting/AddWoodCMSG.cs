using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// Put a log on a campfire. A success answers with a fresh <see cref="CraftableRecipesSMSG"/> showing the new
  /// burn time; a refusal with an <c>OperationError</c>.
  /// </summary>
  public partial class AddWoodCMSG : ICMSG
  {
    public ulong StructureId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        AddWood = new global::Bnet.AddWoodCMSG { StructureId = StructureId }
      };
    }
  }
}
