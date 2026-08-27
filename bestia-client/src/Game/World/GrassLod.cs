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
    /// What is left of a cell's share once the budget has been taken out of it by distance.
    /// </summary>
    /// <remarks>
    /// <b>A full cell stays full at every exponent, and that is the whole point.</b>
    /// <see cref="FractionAt"/> already returns 1 everywhere inside the full-density band and tapers to 0 at
    /// the fade, so raising it to a power takes everything out of the far field and <i>exactly nothing</i> out
    /// of the near one - <c>pow(1, k)</c> is 1 for every k. That is what a flat
    /// <see cref="BudgetTrim"/> cannot do: a single multiplier applied to every cell coarsens the grass at the
    /// player's feet just as hard as the grass at the horizon, which is why the fade radius had to be kept
    /// short to stay affordable.
    ///
    /// <para>
    /// It also composes with <see cref="CoverageScale"/> without any special case, because what comes out is
    /// still a fraction in [0, 1] and the compensation only ever sees the number the cell is actually drawing
    /// at. What the far field loses in coverage is now the terrain shader's to give back - see
    /// <c>grass_field_correction</c> in <c>terrain_common.gdshaderinc</c> - which is what makes thinning it
    /// this hard safe at all.
    /// </para>
    ///
    /// <para>
    /// An <paramref name="exponent"/> of 1 is the identity, so it is the off switch.
    /// </para>
    /// </remarks>
    public static float Sharpen(float fraction, float exponent)
    {
      if (fraction >= 1.0f)
      {
        return 1.0f;
      }

      if (fraction <= 0.0f)
      {
        return 0.0f;
      }

      return exponent <= 1.0f ? fraction : Mathf.Pow(fraction, exponent);
    }

    /// <summary>
    /// The exponent to sharpen with next frame, nudged toward one that fits the field inside its budget.
    /// </summary>
    /// <remarks>
    /// A damped proportional controller in log space rather than a solve. The exponent that lands exactly on
    /// the budget has no closed form - it depends on how much of the view volume is grassy and where - and
    /// bisecting for it would walk every cell several times a frame. Multiplying by the overshoot raised to
    /// <paramref name="rate"/> converges geometrically from one pass, and at a rate of a half it is inside a
    /// few percent within three or four frames.
    ///
    /// <para>
    /// <b>It is also the smoothing, which is why nothing else smooths.</b> The wedge in
    /// <c>TerrainGrass._Process</c> makes <paramref name="wanted"/> jump when a meadow swings into view, and a
    /// controller that walks to meet it is a field that eases rather than one that snaps. <see cref="BudgetTrim"/>
    /// stays on top as a hard ceiling for the frames in between, and returns 1 once this has settled.
    /// </para>
    ///
    /// <para>
    /// A <paramref name="maxExponent"/> of 1 pins this at 1, which gives back the flat trim exactly.
    /// </para>
    /// </remarks>
    public static float NextExponent(float current, int wanted, int maxVisible, float maxExponent, float rate)
    {
      var ceiling = Mathf.Max(maxExponent, 1.0f);

      // No budget, or nothing asking for it. Either way there is nothing to sharpen away, and the neutral
      // exponent is the one that leaves FractionAt's own taper alone.
      if (maxVisible <= 0 || wanted <= 0)
      {
        return 1.0f;
      }

      var step = Mathf.Pow(wanted / (float)maxVisible, Mathf.Clamp(rate, 0.0f, 1.0f));

      return Mathf.Clamp(Mathf.Max(current, 1.0f) * step, 1.0f, ceiling);
    }

    /// <summary>
    /// Half-angle, in radians, of the ground the camera could be looking at.
    /// </summary>
    /// <remarks>
    /// <b>The horizontal field of view is derived rather than named, because Godot does not store it.</b>
    /// <c>Camera3D.Fov</c> is the <i>vertical</i> angle whenever <c>KeepAspect</c> is Keep Height, which is
    /// its default - so the 65 degrees on the spring arm's camera is 97 across a 16:9 screen and more than
    /// that on an ultrawide. A hard-coded wedge would quietly thin the field on exactly the monitors that show
    /// the most of it.
    ///
    /// <para>
    /// Clamped at a half-turn so the wedge saturates at the whole disc: a
    /// <paramref name="marginDegrees"/> of 180 counts every cell and reproduces the behaviour from before
    /// there was a wedge at all. Without the clamp the cosine of 228 degrees is only -0.66 and the ground
    /// directly behind the player would fall back <i>out</i> of a wedge that was meant to be everything.
    /// </para>
    ///
    /// <para>
    /// A degenerate viewport or field of view returns the half-turn for the same reason: the failure of this
    /// measurement is a field that goes thin, and counting too much only costs a little of the budget.
    /// </para>
    /// </remarks>
    public static float HalfViewAngle(float verticalFovDegrees, float aspect, float marginDegrees)
    {
      if (aspect <= 0.0f || verticalFovDegrees <= 0.0f)
      {
        return Mathf.Pi;
      }

      var horizontal = 2.0f * Mathf.Atan(Mathf.Tan(Mathf.DegToRad(verticalFovDegrees) * 0.5f) * aspect);

      return Mathf.Min(horizontal * 0.5f + Mathf.DegToRad(Mathf.Max(marginDegrees, 0.0f)), Mathf.Pi);
    }

    /// <summary>
    /// Whether a cell is ground the camera could be looking at.
    /// </summary>
    /// <remarks>
    /// <b>This decides what competes for the budget, not what is drawn.</b> Godot already culls an off-screen
    /// <c>MultiMeshInstance3D</c> for nothing, so the grass behind the player costs no triangles - but
    /// <c>TerrainGrass</c> was still counting it when totalling what the field asks for, and then thinning
    /// everything by that ratio. On-screen grass was being paid for out of grass nobody can see.
    ///
    /// <para>
    /// Measured in the XZ plane. The field is a layer on the ground and the camera pitches down onto it, so
    /// the bearing is the whole of what separates a cell that is on screen from one that is behind the
    /// player; the pitch only decides how much of the wedge is filled.
    /// </para>
    ///
    /// <para>
    /// <paramref name="nearMetres"/> is the guard at the apex. The wedge is measured from the <i>eye</i>,
    /// which sits metres behind the player, so ground beside and even a little behind the camera is still in
    /// front of the character and plainly on screen. Everything within that radius is counted whatever its
    /// bearing.
    /// </para>
    ///
    /// <para>
    /// Fails <b>open</b>: a camera with no horizontal bearing at all - looking straight down - counts every
    /// cell. The same argument <c>TerrainGrass.Eye</c> makes for drawing at full density when there is no
    /// camera yet, and for the same reason: a frame of grass drawn too densely is a frame of grass, and a
    /// field that silently empties is a feature that does nothing.
    /// </para>
    /// </remarks>
    public static bool InView(Vector3 point, Vector3 eye, Vector3 forward, float cosHalfAngle, float nearMetres)
    {
      var toX = point.X - eye.X;
      var toZ = point.Z - eye.Z;
      var span = toX * toX + toZ * toZ;

      if (span <= nearMetres * nearMetres)
      {
        return true;
      }

      var aheadX = forward.X;
      var aheadZ = forward.Z;
      var ahead = aheadX * aheadX + aheadZ * aheadZ;

      if (ahead <= 0.000001f)
      {
        return true;
      }

      // One square root rather than two normalisations, and no allocation: this runs per cell per frame.
      return (toX * aheadX + toZ * aheadZ) / Mathf.Sqrt(span * ahead) >= cosHalfAngle;
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
    /// <b>A backstop now rather than the main mechanism.</b> <see cref="Sharpen"/> is what actually fits the
    /// field into the budget, and it does it by distance so the near field is never touched. This stays on top
    /// as a hard ceiling for the few frames <see cref="NextExponent"/> takes to catch up after the view swings,
    /// and returns 1 once it has.
    /// </para>
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
