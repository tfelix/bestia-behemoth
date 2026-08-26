using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The arithmetic behind <see cref="TerrainGrass"/>'s level of detail: how much of a cell to draw, how big to
  /// draw it, and how to stay inside a budget.
  /// </summary>
  /// <remarks>
  /// Static and free of every Godot node type on purpose. <see cref="TerrainGrass"/> is a <c>Node3D</c> and
  /// cannot be constructed without the engine, and <c>BestiaClient.Tests</c> deliberately runs without one - so
  /// arithmetic left inside the node is arithmetic that can only be judged by eye. This is the same split
  /// <c>BlockAppearance</c> takes palette entries for.
  ///
  /// <para>
  /// <c>Mathf</c> is a plain managed helper and stays: the test project's own comment is that <c>Vector3</c>,
  /// <c>Color</c> and <c>Mathf</c> "are plain managed value types, so the mesher runs anywhere .NET does".
  /// </para>
  /// </remarks>
  public static class GrassLod
  {
    /// <summary>
    /// How much wider the fade band gets at this camera distance, never below 1.
    /// </summary>
    /// <remarks>
    /// <b>The band has to follow the zoom because the zoom is the same size as the band.</b> The spring arm runs
    /// 8 m to 36 m while the band spans 12 m to 40 m, so a fixed band measured from anywhere is a band the zoom
    /// can consume outright - which is the bug this exists to close. Widening it keeps roughly as much ground
    /// covered on screen at 36 m as at 10 m.
    ///
    /// <para>
    /// Sub-linear at <paramref name="response"/> below 1, because the ground on screen grows with the square of
    /// the distance and the triangles would grow with it. <paramref name="response"/> of <b>0 reproduces a fixed
    /// band exactly</b> - <c>pow(x, 0)</c> is 1 for every x - which is what makes "did the zoom response cause
    /// this" answerable by setting one number to zero.
    /// </para>
    ///
    /// <para>
    /// Clamped at 1 from below so zooming *in* past the reference does not shrink the field. The near band is
    /// already what the player is standing in; there is nothing to save there and a shrinking field would thin
    /// the grass at their feet, which is the symptom rather than the fix.
    /// </para>
    /// </remarks>
    public static float BandScale(float zoomMetres, float referenceZoomMetres, float response, float maxScale)
    {
      if (referenceZoomMetres <= 0.0f || zoomMetres <= referenceZoomMetres)
      {
        return 1.0f;
      }

      var scale = Mathf.Pow(zoomMetres / referenceZoomMetres, response);

      return Mathf.Clamp(scale, 1.0f, Mathf.Max(maxScale, 1.0f));
    }

    /// <summary>What share of a cell's clumps to draw at this distance from it, 1 down to 0.</summary>
    /// <remarks>
    /// Squared rather than linear, so most of the saving is taken in the first few metres past
    /// <paramref name="fullMetres"/> where there is most ground to save it on, and the last clumps go out
    /// gradually instead of the far edge of the field stepping.
    /// </remarks>
    public static float FractionAt(float distance, float fullMetres, float fadeMetres)
    {
      if (distance <= fullMetres)
      {
        return 1.0f;
      }

      if (distance >= fadeMetres)
      {
        return 0.0f;
      }

      var span = Mathf.Max(fadeMetres - fullMetres, 0.001f);
      var t = 1.0f - (distance - fullMetres) / span;

      return t * t;
    }

    /// <summary>
    /// How much bigger to draw each surviving clump so that a thinned cell still covers its ground.
    /// </summary>
    /// <remarks>
    /// <b>Why thinning is visible at all.</b> How much of the ground a cell hides is
    /// <c>count * footprint</c>, and a clump's footprint goes with the square of its scale. So dropping the
    /// count to a fraction <c>f</c> and scaling what is left by <c>1/sqrt(f)</c> leaves the product where it
    /// was - the field holds its coverage and what changes is the grain, from many small clumps to fewer larger
    /// ones. That identity is the whole trick, and it is what
    /// <c>GrassLodTest.CoverageIsHeldWhileTheScaleHasHeadroom</c> pins.
    ///
    /// <para>
    /// <paramref name="maxScale"/> is where it stops, and past that point coverage does fall again - which is
    /// wanted rather than tolerated. Compensation all the way down would end the field in clumps the size of
    /// bushes; saturating instead means the far edge fades out as before, only much further away than the
    /// uncompensated one did.
    /// </para>
    ///
    /// <para>
    /// <paramref name="strength"/> exists to be turned to 0 in the inspector while the game runs. Coarser grain
    /// at distance is a look, and a look is judged by eye against the alternative rather than argued about.
    /// </para>
    /// </remarks>
    /// <returns>A multiplier on the clump's own scale, never below 1.</returns>
    public static float CoverageScale(float fraction, float strength, float maxScale)
    {
      // A cell drawing nothing is not thin, it is absent, and 1/sqrt(0) is not a number. Anything at or below
      // zero keeps the neutral scale so a hidden cell settles on one value and stops being written to.
      if (fraction <= 0.0f || fraction >= 1.0f)
      {
        return 1.0f;
      }

      var ideal = Mathf.Min(1.0f / Mathf.Sqrt(fraction), Mathf.Max(maxScale, 1.0f));

      return Mathf.Lerp(1.0f, ideal, Mathf.Clamp(strength, 0.0f, 1.0f));
    }

    /// <summary>
    /// The factor every cell's share is multiplied by to bring the whole field inside its instance budget.
    /// </summary>
    /// <remarks>
    /// <b>This is what makes the range safe to turn up</b>, and it is <c>BuildBudgetMillis</c>'s argument moved
    /// from build cost to draw cost: raising <c>FadeOutMetres</c> should make the field *reach further* rather
    /// than make frames longer. Without it, the band scaling above turns a zoom-out into an unbounded triangle
    /// count, since the ground on screen grows with the square of the camera distance.
    ///
    /// <para>
    /// Proportional rather than a cutoff at some radius, because a cutoff moves the fade edge inward under load
    /// - the field visibly retreats. Trimming everything by the same share thins uniformly instead, and the
    /// coverage compensation above is fed the trimmed fraction, so what the budget takes in count it gives back
    /// in clump size for as long as that has headroom.
    /// </para>
    ///
    /// <para>
    /// A <paramref name="maxVisible"/> of zero or less means no budget at all.
    /// </para>
    /// </remarks>
    public static float BudgetTrim(int wantedTotal, int maxVisible)
    {
      if (maxVisible <= 0 || wantedTotal <= maxVisible)
      {
        return 1.0f;
      }

      return maxVisible / (float)wantedTotal;
    }

    /// <summary>
    /// Distance from a point to an axis-aligned box, and zero when the point is inside it.
    /// </summary>
    /// <remarks>
    /// How far outside each face the point is, or zero on the axes where it lies between them. Measured to the
    /// <i>nearest</i> point of a cell rather than to its middle, so a cell the player is standing at the edge of
    /// is at full density rather than at whatever a half-cell offset works out to.
    /// </remarks>
    public static float DistanceToBox(Vector3 point, Vector3 min, Vector3 max) =>
      (min - point).Max(point - max).Max(Vector3.Zero).Length();
  }
}
