namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// One mark left on the ground at a point: a footprint, a splatter.
  /// </summary>
  /// <remarks>
  /// A record rather than pixels, which is what keeps how good it looks a client decision. The same stamp
  /// renders as a darkened patch, a normal-mapped dent, or displaced geometry, and the protocol never hears
  /// about the difference - so the fidelity can be raised later without a server change.
  ///
  /// <para>
  /// A grid of levels cannot express this: a print is about thirty centimetres across against a one metre
  /// cell, so a grid can say that ground was walked but never <i>what</i> walked it, or which way.
  /// </para>
  /// </remarks>
  /// <param name="Channel">
  /// The composited channel this belongs to, already resolved from the wire id - see
  /// <see cref="Game.World.GroundLayers.ChannelOf"/>.
  /// </param>
  /// <param name="Rotation">Heading in 256ths of a turn. Movement is eight-connected, so eight values occur.</param>
  /// <param name="Seed">Picks a variant, so a hundred prints are not one print a hundred times.</param>
  /// <param name="AtSecond">The Bestia second it was made, so this fades without being told again.</param>
  public readonly record struct GroundStamp(
    long X,
    long Y,
    int Channel,
    int Brush,
    int Rotation,
    int Seed,
    long AtSecond);
}
