using BestiaBehemothClient.Game.World;
using Godot;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// The grass field's level of detail: how much of a cell is drawn, how large, and inside what budget.
  /// </summary>
  /// <remarks>
  /// Written against a bug that had already shipped. The field measured its distance from the *camera*, and the
  /// spring arm puts the camera 8 m to 36 m from the player while the fade band spanned 12 m to 40 m - so the
  /// zoom and the band were the same size and zooming out consumed the whole of it. At full zoom the ground the
  /// player was standing on drew about a fiftieth of its grass.
  ///
  /// <para>
  /// The arithmetic lives in <see cref="GrassLod"/> rather than in <c>TerrainGrass</c> precisely so it can be
  /// pinned here: <c>TerrainGrass</c> is a <c>Node3D</c> and this project runs without an engine, so anything
  /// left inside the node can only be judged by eye.
  /// </para>
  /// </remarks>
  public class GrassLodTest
  {
    private const float Full = 15.0f;
    private const float Fade = 60.0f;

    /// <summary>
    /// Zooming out never thins a cell, at any distance and any response.
    /// </summary>
    /// <remarks>
    /// <b>The bug, stated as a property.</b> How much grass stands at a point on the ground is a fact about that
    /// ground and about where the player is; it cannot be a fact about how far back the camera is sitting. Since
    /// the band only ever widens, a fraction at a fixed distance can only rise with the zoom - so this fails if
    /// anyone reintroduces a term that shrinks the band, whatever else they change.
    /// </remarks>
    [Fact]
    public void ZoomingOutNeverThinsACell()
    {
      foreach (var distance in new[] { 0.0f, 5.0f, 14.9f, 20.0f, 35.0f, 55.0f, 90.0f })
      {
        var previous = 0.0f;

        // The spring arm's own range, min_cam_distance to max_cam_distance.
        for (var zoom = 8.0f; zoom <= 36.0f; zoom += 1.0f)
        {
          var band = GrassLod.BandScale(zoom, 10.0f, 0.5f, 2.5f);
          var fraction = GrassLod.FractionAt(distance, Full * band, Fade * band);

          Assert.True(
            fraction >= previous,
            $"at {distance} m the fraction fell from {previous} to {fraction} on zooming out to {zoom} m");

          previous = fraction;
        }
      }
    }

    /// <summary>
    /// The measurement from the commit message: what the old anchor did to grass under the player's feet.
    /// </summary>
    /// <remarks>
    /// Kept as a test rather than only in prose because it is the number that justifies the whole change, and
    /// because it is the one thing a future refactor could quietly undo. Measured from the camera the field is
    /// all but gone; measured from the player it is untouched.
    /// </remarks>
    [Fact]
    public void GrassUnderThePlayerSurvivesFullZoomOnlyWhenMeasuredFromThem()
    {
      // Eye to the ground at the player's feet at maximum zoom and the shallowest pitch the camera allows.
      const float EyeToFeetAtFullZoom = 36.5f;

      // The band the field shipped with, named here rather than reusing the current one: this half of the test
      // is a statement about what the old code did, and it would quietly stop being one if it drifted with the
      // tuning above.
      const float ShippedFull = 12.0f;
      const float ShippedFade = 40.0f;

      var fromTheEye = GrassLod.FractionAt(EyeToFeetAtFullZoom, ShippedFull, ShippedFade);
      Assert.InRange(fromTheEye, 0.0f, 0.02f);

      var band = GrassLod.BandScale(EyeToFeetAtFullZoom, 10.0f, 0.5f, 2.5f);
      Assert.Equal(1.0f, GrassLod.FractionAt(0.0f, Full * band, Fade * band));
    }

    /// <summary>A response of zero pins the band, which is how the feature is turned off.</summary>
    [Fact]
    public void AZeroResponsePinsTheBand()
    {
      for (var zoom = 1.0f; zoom <= 100.0f; zoom += 7.0f)
      {
        Assert.Equal(1.0f, GrassLod.BandScale(zoom, 10.0f, 0.0f, 2.5f));
      }
    }

    /// <summary>Zooming in past the reference does not shrink the field the player is standing in.</summary>
    [Fact]
    public void ZoomingInsideTheReferenceLeavesTheBandAlone()
    {
      Assert.Equal(1.0f, GrassLod.BandScale(8.0f, 10.0f, 0.5f, 2.5f));
      Assert.Equal(1.0f, GrassLod.BandScale(0.0f, 10.0f, 0.5f, 2.5f));
    }

    [Fact]
    public void TheBandGrowsWithTheZoomUpToItsCeiling()
    {
      // Four times the reference at a response of a half is twice the band.
      Assert.Equal(2.0f, GrassLod.BandScale(40.0f, 10.0f, 0.5f, 4.0f), 4);

      Assert.Equal(1.5f, GrassLod.BandScale(40.0f, 10.0f, 0.5f, 1.5f));
    }

    /// <summary>A reference of zero cannot divide, and answers the neutral band rather than throwing.</summary>
    [Fact]
    public void ADegenerateReferenceIsNeutral()
    {
      Assert.Equal(1.0f, GrassLod.BandScale(20.0f, 0.0f, 0.5f, 2.5f));
    }

    [Fact]
    public void TheFractionIsWholeInsideTheFullBandAndGoneOutsideTheFade()
    {
      Assert.Equal(1.0f, GrassLod.FractionAt(0.0f, Full, Fade));
      Assert.Equal(1.0f, GrassLod.FractionAt(Full, Full, Fade));

      Assert.Equal(0.0f, GrassLod.FractionAt(Fade, Full, Fade));
      Assert.Equal(0.0f, GrassLod.FractionAt(Fade + 100.0f, Full, Fade));
    }

    [Fact]
    public void TheFractionFallsAcrossTheTaper()
    {
      var previous = 1.0f;

      for (var distance = Full; distance <= Fade; distance += 0.5f)
      {
        var fraction = GrassLod.FractionAt(distance, Full, Fade);

        Assert.InRange(fraction, 0.0f, previous);
        previous = fraction;
      }
    }

    /// <summary>
    /// Coverage is held while the scale has headroom, which is the identity the compensation rests on.
    /// </summary>
    /// <remarks>
    /// How much ground a cell hides is <c>count * footprint</c> and a footprint goes with the square of the
    /// scale, so thinning to <c>f</c> and scaling by <c>1/sqrt(f)</c> has to leave the product alone. If this
    /// fails, distant grass bares the ground - which is the symptom the compensation exists to remove, and one
    /// that is easy to mistake for the fade being tuned wrong.
    /// </remarks>
    [Fact]
    public void CoverageIsHeldWhileTheScaleHasHeadroom()
    {
      const double Total = 1000.0;
      const float MaxScale = 2.5f;

      // Above 1/MaxScale² = 0.16, where the clamp has not bitten yet.
      foreach (var fraction in new[] { 1.0f, 0.9f, 0.6f, 0.4f, 0.25f, 0.17f })
      {
        var scale = GrassLod.CoverageScale(fraction, 1.0f, MaxScale);
        var coverage = Total * fraction * scale * scale;

        Assert.Equal(Total, coverage, 1);
      }
    }

    /// <summary>
    /// Past the ceiling the field goes back to fading, which is wanted rather than tolerated.
    /// </summary>
    /// <remarks>
    /// Compensating the whole way down would end the field in clumps the size of bushes. Saturating means the
    /// far edge still fades out, only much further away than the uncompensated one did.
    /// </remarks>
    [Fact]
    public void TheCompensationSaturatesRatherThanGrowingWithoutBound()
    {
      Assert.Equal(2.5f, GrassLod.CoverageScale(0.01f, 1.0f, 2.5f));
      Assert.Equal(2.5f, GrassLod.CoverageScale(0.0001f, 1.0f, 2.5f));
    }

    /// <summary>A cell drawing nothing is not thin, and <c>1/sqrt(0)</c> is not a number.</summary>
    /// <remarks>
    /// It matters that this is exactly 1: a hidden cell settling on one value is what stops
    /// <c>TerrainGrass.Retune</c> pushing a uniform every frame for the several hundred cells past the fade
    /// radius, which is most of what the terrain holds.
    /// </remarks>
    [Fact]
    public void AnEmptyOrWholeCellTakesTheNeutralScale()
    {
      Assert.Equal(1.0f, GrassLod.CoverageScale(0.0f, 1.0f, 2.5f));
      Assert.Equal(1.0f, GrassLod.CoverageScale(-1.0f, 1.0f, 2.5f));
      Assert.Equal(1.0f, GrassLod.CoverageScale(1.0f, 1.0f, 2.5f));
    }

    /// <summary>Zero strength is the uncompensated field, which is the point of the export.</summary>
    [Fact]
    public void ZeroStrengthLeavesTheClumpsAlone()
    {
      foreach (var fraction in new[] { 0.75f, 0.5f, 0.2f, 0.05f })
      {
        Assert.Equal(1.0f, GrassLod.CoverageScale(fraction, 0.0f, 2.5f));
      }
    }

    [Fact]
    public void HalfStrengthSitsBetweenTheTwo()
    {
      var full = GrassLod.CoverageScale(0.25f, 1.0f, 2.5f);
      var half = GrassLod.CoverageScale(0.25f, 0.5f, 2.5f);

      Assert.Equal((1.0f + full) / 2.0f, half, 4);
    }

    [Fact]
    public void AFieldInsideItsBudgetIsNotTrimmed()
    {
      Assert.Equal(1.0f, GrassLod.BudgetTrim(0, 40_000));
      Assert.Equal(1.0f, GrassLod.BudgetTrim(39_999, 40_000));
      Assert.Equal(1.0f, GrassLod.BudgetTrim(40_000, 40_000));
    }

    /// <summary>A budget of zero or less is no budget, not a field of no grass.</summary>
    [Fact]
    public void AnUnsetBudgetDoesNotTrim()
    {
      Assert.Equal(1.0f, GrassLod.BudgetTrim(500_000, 0));
      Assert.Equal(1.0f, GrassLod.BudgetTrim(500_000, -1));
    }

    /// <summary>Over budget, the trim brings the field to the cap and never raises a cell.</summary>
    [Fact]
    public void TheTrimBringsTheFieldInsideItsBudget()
    {
      const int Budget = 40_000;

      foreach (var wanted in new[] { 40_001, 60_000, 120_000, 900_000 })
      {
        var trim = GrassLod.BudgetTrim(wanted, Budget);

        Assert.InRange(trim, 0.0f, 1.0f);
        Assert.True(wanted * trim <= Budget + 1, $"{wanted} trimmed to {wanted * trim}, over a {Budget} budget");
      }
    }

    [Fact]
    public void APointInsideTheBoxIsAtNoDistanceFromIt()
    {
      var min = new Vector3(0.0f, 0.0f, 0.0f);
      var max = new Vector3(8.0f, 2.0f, 8.0f);

      Assert.Equal(0.0f, GrassLod.DistanceToBox(new Vector3(4.0f, 1.0f, 4.0f), min, max));
      Assert.Equal(0.0f, GrassLod.DistanceToBox(min, min, max));
    }

    /// <summary>
    /// A point outside is measured to the nearest face, not to the middle.
    /// </summary>
    /// <remarks>
    /// The reason a cell the player is standing at the edge of draws at full density rather than at whatever a
    /// half-cell offset works out to.
    /// </remarks>
    [Fact]
    public void APointOutsideIsMeasuredToTheNearestFace()
    {
      var min = new Vector3(0.0f, 0.0f, 0.0f);
      var max = new Vector3(8.0f, 2.0f, 8.0f);

      Assert.Equal(2.0f, GrassLod.DistanceToBox(new Vector3(10.0f, 1.0f, 4.0f), min, max));

      // Diagonally off one corner: 3-4-5 in the horizontal plane, and between the faces vertically.
      Assert.Equal(5.0f, GrassLod.DistanceToBox(new Vector3(11.0f, 1.0f, 12.0f), min, max), 4);
    }

    /// <summary>
    /// The ground the player is standing on is never thinned to pay for the ground at the horizon.
    /// </summary>
    /// <remarks>
    /// <b>The property the whole distance-weighted budget rests on, and the reason it replaced a flat trim.</b>
    /// A single multiplier across the field coarsened the grass at the player's feet exactly as hard as the
    /// grass at the fade radius, so reaching further always cost something up close - which is why the radius
    /// was kept at sixty metres. Sharpening cannot: <c>pow(1, k)</c> is 1 for every k, and
    /// <see cref="GrassLod.FractionAt"/> returns exactly 1 everywhere inside the full-density band.
    ///
    /// <para>
    /// Stated over the two together rather than over <see cref="GrassLod.Sharpen"/> alone, because it is the
    /// pair that has to hold: a taper that stopped returning a clean 1 near the player would break this while
    /// leaving <c>Sharpen</c> itself perfectly correct.
    /// </para>
    /// </remarks>
    [Fact]
    public void SharpeningNeverThinsTheGroundThePlayerStandsOn()
    {
      foreach (var exponent in new[] { 1.0f, 1.5f, 2.3f, 6.0f, 40.0f })
      {
        foreach (var distance in new[] { 0.0f, 1.0f, 7.5f, Full })
        {
          Assert.Equal(1.0f, GrassLod.Sharpen(GrassLod.FractionAt(distance, Full, Fade), exponent));
        }
      }
    }

    /// <summary>
    /// Sharpening only ever takes, it takes more the further out a cell is, and it takes more the higher the
    /// exponent.
    /// </summary>
    [Fact]
    public void SharpeningOnlyEverTakesAndTakesMonotonically()
    {
      foreach (var fraction in new[] { 0.9f, 0.5f, 0.2f, 0.05f })
      {
        var previous = fraction;

        foreach (var exponent in new[] { 1.0f, 1.5f, 2.0f, 3.0f, 6.0f })
        {
          var sharpened = GrassLod.Sharpen(fraction, exponent);

          Assert.InRange(sharpened, 0.0f, previous);
          previous = sharpened;
        }
      }
    }

    /// <summary>
    /// An exponent of one is the identity, which is what makes <c>MaxExponent</c> a switch.
    /// </summary>
    /// <remarks>
    /// Turning it to 1 in the inspector gives back the flat <see cref="GrassLod.BudgetTrim"/> exactly, which is
    /// how the distance weighting is judged against the alternative in the running game.
    /// </remarks>
    [Fact]
    public void AUnitExponentIsTheIdentity()
    {
      foreach (var fraction in new[] { 0.0f, 0.05f, 0.5f, 0.99f, 1.0f })
      {
        Assert.Equal(fraction, GrassLod.Sharpen(fraction, 1.0f));
      }
    }

    /// <summary>An empty cell stays empty, and one past the fade cannot come back.</summary>
    [Fact]
    public void AnEmptyCellStaysEmptyAtEveryExponent()
    {
      foreach (var exponent in new[] { 1.0f, 2.5f, 6.0f })
      {
        Assert.Equal(0.0f, GrassLod.Sharpen(0.0f, exponent));
        Assert.Equal(0.0f, GrassLod.Sharpen(-1.0f, exponent));
      }
    }

    /// <summary>The exponent rises when the field is over budget and falls back when it is under.</summary>
    [Fact]
    public void TheExponentRisesOverBudgetAndFallsBackUnderIt()
    {
      // Twice the budget, corrected at a half: the square root of two.
      Assert.Equal(1.4142f, GrassLod.NextExponent(1.0f, 40_000, 20_000, 6.0f, 0.5f), 3);

      // Half the budget, from an exponent of two: the same step, downward.
      Assert.Equal(1.4142f, GrassLod.NextExponent(2.0f, 10_000, 20_000, 6.0f, 0.5f), 3);

      // Exactly on budget holds still, whatever it is currently at.
      Assert.Equal(2.5f, GrassLod.NextExponent(2.5f, 20_000, 20_000, 6.0f, 0.5f), 4);
    }

    /// <summary>The exponent never leaves its bounds, however far off the budget the field is.</summary>
    /// <remarks>
    /// Below 1 it would <i>add</i> grass at distance rather than removing it, which is not a state the field
    /// has any way to pay for. Above the ceiling one pathological frame could empty the far field outright.
    /// </remarks>
    [Fact]
    public void TheExponentStaysInsideItsBounds()
    {
      Assert.Equal(1.0f, GrassLod.NextExponent(1.0f, 1, 20_000, 6.0f, 0.5f));
      Assert.Equal(6.0f, GrassLod.NextExponent(5.0f, 1_000_000, 10_000, 6.0f, 0.5f));

      // A ceiling of 1 pins it, which is the off switch stated as a bound.
      Assert.Equal(1.0f, GrassLod.NextExponent(3.0f, 100_000, 10_000, 1.0f, 0.5f));
    }

    /// <summary>No budget, or nothing asking for one, leaves the taper alone.</summary>
    [Fact]
    public void AnUnsetBudgetPinsTheExponent()
    {
      Assert.Equal(1.0f, GrassLod.NextExponent(3.0f, 40_000, 0, 6.0f, 0.5f));
      Assert.Equal(1.0f, GrassLod.NextExponent(3.0f, 0, 20_000, 6.0f, 0.5f));
    }

    /// <summary>A response of zero holds the exponent wherever it is.</summary>
    [Fact]
    public void AZeroResponsePinsTheExponent()
    {
      Assert.Equal(2.5f, GrassLod.NextExponent(2.5f, 90_000, 18_000, 6.0f, 0.0f), 4);
    }

    /// <summary>
    /// The controller actually converges on a field that fits, rather than merely stepping in the right
    /// direction.
    /// </summary>
    /// <remarks>
    /// A stand-in field whose count falls as one over the exponent, which has a fixed point at 2.78 for this
    /// budget. What is being pinned is that repeated application settles there instead of ringing or walking
    /// off to the ceiling - the reason nothing else in the loop smooths.
    /// </remarks>
    [Fact]
    public void TheExponentSettlesOnAFieldThatFits()
    {
      const int Budget = 18_000;

      var exponent = 1.0f;
      var wanted = 0;

      for (var frame = 0; frame < 40; frame++)
      {
        wanted = (int)(50_000.0f / exponent);
        exponent = GrassLod.NextExponent(exponent, wanted, Budget, 6.0f, 0.5f);
      }

      Assert.InRange(wanted, (int)(Budget * 0.95f), (int)(Budget * 1.05f));
      Assert.InRange(exponent, 1.0f, 6.0f);
    }

    /// <summary>
    /// The wedge follows the shape of the viewport, because Godot only stores the vertical angle.
    /// </summary>
    /// <remarks>
    /// <c>Camera3D.Fov</c> is vertical under the default Keep Height, so the spring arm camera's 65 degrees is
    /// 97 across a 16:9 screen and more than that on an ultrawide. A hard-coded wedge would thin the field on
    /// exactly the monitors that show the most of it.
    /// </remarks>
    [Fact]
    public void TheWedgeFollowsTheViewportShape()
    {
      var wide = Mathf.RadToDeg(GrassLod.HalfViewAngle(65.0f, 16.0f / 9.0f, 0.0f));
      var square = Mathf.RadToDeg(GrassLod.HalfViewAngle(65.0f, 1.0f, 0.0f));

      Assert.Equal(48.56f, wide, 1);
      Assert.Equal(32.5f, square, 1);
      Assert.True(wide > square);

      // The margin is added on top, in degrees.
      Assert.Equal(square + 20.0f, Mathf.RadToDeg(GrassLod.HalfViewAngle(65.0f, 1.0f, 20.0f)), 1);
    }

    /// <summary>
    /// A margin of 180 saturates the wedge at the whole disc rather than wrapping past it.
    /// </summary>
    /// <remarks>
    /// <b>The clamp is what makes the off switch exact.</b> Without it a half-angle of 228 degrees has a cosine
    /// of -0.66, so ground directly behind the player - at a dot product of -1 - would fall back <i>out</i> of a
    /// wedge that was meant to be everything, and turning the feature off would thin the field instead.
    /// </remarks>
    [Fact]
    public void AFullMarginCountsEverything()
    {
      var half = GrassLod.HalfViewAngle(65.0f, 16.0f / 9.0f, 180.0f);

      Assert.Equal(Mathf.Pi, half, 4);
      Assert.Equal(-1.0f, Mathf.Cos(half), 4);

      var behind = new Vector3(0.0f, 0.0f, 40.0f);

      Assert.True(GrassLod.InView(behind, Vector3.Zero, new Vector3(0.0f, 0.0f, -1.0f), Mathf.Cos(half), 0.0f));
    }

    /// <summary>A viewport or a field of view that measures nothing counts every cell.</summary>
    /// <remarks>
    /// Fails open, like the rest of this: the failure of the measurement is a field that goes thin, and
    /// counting too much only spends a little of the budget.
    /// </remarks>
    [Fact]
    public void ADegenerateViewportCountsEverything()
    {
      Assert.Equal(Mathf.Pi, GrassLod.HalfViewAngle(65.0f, 0.0f, 20.0f), 4);
      Assert.Equal(Mathf.Pi, GrassLod.HalfViewAngle(65.0f, -1.0f, 20.0f), 4);
      Assert.Equal(Mathf.Pi, GrassLod.HalfViewAngle(0.0f, 1.778f, 20.0f), 4);
    }

    /// <summary>
    /// Ground ahead of the camera competes for the budget and ground behind it does not.
    /// </summary>
    /// <remarks>
    /// The whole point of the wedge. Godot culls the grass behind the player for the price of one bounds test,
    /// but it was still being counted when the field totalled what it wanted - so the grass on screen was
    /// thinned to pay for grass nobody can see.
    /// </remarks>
    [Fact]
    public void GroundAheadIsCountedAndGroundBehindIsNot()
    {
      var eye = Vector3.Zero;
      var forward = new Vector3(0.0f, 0.0f, -1.0f);
      var cos = Mathf.Cos(Mathf.DegToRad(60.0f));

      Assert.True(GrassLod.InView(new Vector3(0.0f, 0.0f, -50.0f), eye, forward, cos, 0.0f));
      Assert.True(GrassLod.InView(new Vector3(40.0f, 0.0f, -50.0f), eye, forward, cos, 0.0f));

      Assert.False(GrassLod.InView(new Vector3(0.0f, 0.0f, 50.0f), eye, forward, cos, 0.0f));
      Assert.False(GrassLod.InView(new Vector3(50.0f, 0.0f, 0.0f), eye, forward, cos, 0.0f));

      // Height is not part of the test: the field is a layer on the ground and the camera pitches onto it, so
      // the bearing is the whole of what separates on screen from behind.
      Assert.True(GrassLod.InView(new Vector3(0.0f, 30.0f, -50.0f), eye, forward, cos, 0.0f));
    }

    /// <summary>
    /// Ground close to the eye is counted whatever its bearing, because it is still in front of the player.
    /// </summary>
    /// <remarks>
    /// The wedge is measured from the camera, which sits metres behind the character on the spring arm - so
    /// ground beside and even a little behind the eye is plainly on screen. That radius is the zoom, which is
    /// why <c>TerrainGrass</c> derives it rather than exporting it.
    /// </remarks>
    [Fact]
    public void GroundBesideTheEyeIsCountedWhateverItsBearing()
    {
      var eye = Vector3.Zero;
      var forward = new Vector3(0.0f, 0.0f, -1.0f);
      var cos = Mathf.Cos(Mathf.DegToRad(60.0f));

      Assert.True(GrassLod.InView(new Vector3(0.0f, 0.0f, 10.0f), eye, forward, cos, 20.0f));
      Assert.False(GrassLod.InView(new Vector3(0.0f, 0.0f, 30.0f), eye, forward, cos, 20.0f));
    }

    /// <summary>A camera with no bearing at all - looking straight down - counts every cell.</summary>
    [Fact]
    public void ACameraWithNoBearingCountsEverything()
    {
      var cos = Mathf.Cos(Mathf.DegToRad(60.0f));

      Assert.True(GrassLod.InView(new Vector3(0.0f, 0.0f, 50.0f), Vector3.Zero, Vector3.Down, cos, 0.0f));
      Assert.True(GrassLod.InView(new Vector3(0.0f, 0.0f, 50.0f), Vector3.Zero, Vector3.Zero, cos, 0.0f));
    }
  }
}
