using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// Godot-friendly wrapper for OperationSuccess protobuf data
  /// </summary>
  [GlobalClass]
  public partial class OperationSuccess : ISMSG
  {
    [Export]
    public int Code { get; set; }

    /// <summary>
    /// <see cref="Code"/> as its lowercase snake_case enum name, for the GDScript side - the mirror of
    /// <c>OperationError.CodeName</c>, and for the same reason.
    /// </summary>
    [Export]
    public string CodeName { get; set; } = string.Empty;

    /// <summary>
    /// Creates an OperationSuccess message from protobuf data
    /// </summary>
    /// <param name="protoOperationSuccess">The protobuf OperationSuccess object</param>
    /// <returns>Godot-friendly OperationSuccess object</returns>
    public static OperationSuccess FromProto(global::Bnet.OperationSuccess protoOperationSuccess)
    {
      return new OperationSuccess
      {
        Code = (int)protoOperationSuccess.Code,
        CodeName = EnumName.Of(protoOperationSuccess.Code)
      };
    }

    /// <summary>
    /// Gets the success code as the original enum type for C# usage
    /// </summary>
    public global::Bnet.OpSuccess SuccessCode => (global::Bnet.OpSuccess)Code;

    /// <summary>
    /// Checks if the operation was a master creation success
    /// </summary>
    public bool IsMasterCreated => SuccessCode == global::Bnet.OpSuccess.MasterCreated;
  }
}