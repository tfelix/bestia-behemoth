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

    /// <summary>The ramp's width, as a share of a cell. <c>grass.gdshader</c> carries the same default.</summary>
    private const float Span = 0.06f;

    /// <summary>Instance counts a cell realistically holds, from nearly bare to a dense tuft layer.</summary>
    private static readonly int[] Totals = { 30, 100, 288, 1000 };

    /// <summary>How many instances a cell draws at this front, which is what the multimesh is truncated to.</summary>
    private static int Drawn(int total, float front) =>
      Mathf.RoundToInt(total * GrassLod.DrawnFraction(front));

    /// <summary>
    /// An instance nobody sets is fully grown.
    /// </summary>
    /// <remarks>
    /// <b>The contract the rest of the world rests on, and the one that would break silently.</b>
    /// <c>StaticEntityRenderer</c> draws every herb, shrub and reed with this same shader and sets no instance
    /// uniform at all, so the unset case has to mean "already grown" rather than "not yet revealed". Getting it
    /// backwards collapses every ground-cover prop in the world to a point, and nothing in the grass field
    /// would show it.
    /// </remarks>
    [Fact]
    public void AnInstanceNobodySetsIsFullyGrown()
    {
      Assert.Equal(0.0f, GrassLod.RevealStep(0));

      foreach (var index in new[] { 0, 1, 17, 500, 4000 })
      {
        Assert.Equal(1.0f, GrassLod.Reveal(index, GrassLod.RevealStep(0), 0.0f, Span));
      }
    }

    /// <summary>
    /// A cell at full density draws every blade at its authored size.
    /// </summary>
    /// <remarks>
    /// <b>Why <see cref="GrassLod.RevealFront"/> inflates past 1.</b> <see cref="GrassLod.FractionAt"/> returns
    /// exactly 1 everywhere inside the full-density radius, so a front sitting at the fraction itself would
    /// leave the last <c>Span</c> of every cell permanently stunted - the ground under the player's feet worst
    /// of all, where nothing is fading. This fails the moment anyone takes the inflation back out.
    /// </remarks>
    [Fact]
    public void AFullCellIsWhollyGrown()
    {
      var front = GrassLod.RevealFront(1.0f, Span);

      foreach (var total in Totals)
      {
        Assert.Equal(total, Drawn(total, front));

        for (var index = 0; index < total; index++)
        {
          Assert.Equal(1.0f, GrassLod.Reveal(index, GrassLod.RevealStep(total), front, Span));
        }
      }
    }

    /// <summary>An empty cell grows nothing, and settles there.</summary>
    [Fact]
    public void AnEmptyCellGrowsNothing()
    {
      var front = GrassLod.RevealFront(0.0f, Span);

      Assert.Equal(0.0f, front);
      Assert.Equal(0, Drawn(288, front));
      Assert.Equal(0.0f, GrassLod.Reveal(0, GrassLod.RevealStep(288), front, Span));
      Assert.Equal(0.0f, GrassLod.RevealedFraction(front, Span));
    }

    /// <summary>
    /// The blade about to wink out is already too small to see, and the next one is not drawn at all.
    /// </summary>
    /// <remarks>
    /// <b>The pop, stated as a property.</b> The two have to agree: a ramp that ended before the truncation
    /// would put a blade of real size on the boundary - the pop this exists to remove, only smaller - and one
    /// that ended after it would draw instances nobody can see. Centring each blade in its own slot bounds the
    /// frontmost drawn one at <c>1 / (total * span)</c>, which is why a sparse cell is the hardest case and a
    /// dense one is free.
    /// </remarks>
    [Fact]
    public void TheNewestBladeEntersAtNothingAndTheNextIsNotDrawn()
    {
      foreach (var total in Totals)
      {
        var step = GrassLod.RevealStep(total);
        var bound = 1.0f / (total * Span);

        // Swept finely enough to land on both sides of RoundToInt's banker's rounding.
        for (var k = 1; k < 2000; k++)
        {
          var front = GrassLod.RevealFront(k / 2000.0f, Span);

          // Only while the cell is still filling. Once it is full, a full-size last blade is the point.
          if (front > 1.0f)
          {
            continue;
          }

          var drawn = Drawn(total, front);
          if (drawn <= 0)
          {
            continue;
          }

          var last = GrassLod.Reveal(drawn - 1, step, front, Span);
          Assert.True(last <= bound + 0.001f, $"the frontmost blade of {total} was at {last}, over {bound}");

          if (drawn < total)
          {
            Assert.Equal(0.0f, GrassLod.Reveal(drawn, step, front, Span));
          }
        }
      }
    }

    /// <summary>
    /// A blade never shrinks as the player walks towards it.
    /// </summary>
    /// <remarks>
    /// Hysteresis as a property. The reveal is a pure function of distance and holds no state, so walking back
    /// and forth over the same ground has to retrace the same sizes rather than flickering. Fails if anyone
    /// makes the span depend on the fraction.
    /// </remarks>
    [Fact]
    public void ABladeNeverShrinksAsThePlayerApproaches()
    {
      const int Total = 288;
      var step = GrassLod.RevealStep(Total);

      foreach (var index in new[] { 0, 50, 200, 287 })
      {
        var previous = 0.0f;

        for (var k = 0; k <= 200; k++)
        {
          var grown = GrassLod.Reveal(index, step, GrassLod.RevealFront(k / 200.0f, Span), Span);

          Assert.True(grown >= previous, $"blade {index} shrank from {previous} to {grown} on approach");

          previous = grown;
        }
      }
    }

    /// <summary>The reveal is a prefix: no blade is grown past one that comes before it.</summary>
    /// <remarks>
    /// <b>Why <c>TerrainGrass.Scatter</c> shuffles.</b> The multimesh can only truncate its tail, so the shape
    /// drawn is always a prefix of the cell's order - and it is the shuffle that makes a prefix a uniform
    /// subset of the ground rather than of whichever triangles the mesher emitted first.
    /// </remarks>
    [Fact]
    public void TheRevealIsAPrefix()
    {
      const int Total = 288;
      var step = GrassLod.RevealStep(Total);

      foreach (var fraction in new[] { 0.05f, 0.25f, 0.5f, 0.75f, 0.99f })
      {
        var front = GrassLod.RevealFront(fraction, Span);
        var previous = 1.0f;

        for (var index = 0; index < Total; index++)
        {
          var grown = GrassLod.Reveal(index, step, front, Span);

          Assert.True(grown <= previous, $"blade {index} was grown past the one before it");

          previous = grown;
        }
      }
    }

    /// <summary>
    /// The closed form matches what the blades actually add up to.
    /// </summary>
    /// <remarks>
    /// <see cref="GrassLod.RevealedFraction"/> is an integral solved on paper and
    /// <see cref="GrassLod.Reveal"/> is what the shader draws; this is the only thing standing between a wrong
    /// antiderivative and a field that is quietly the wrong density. Coverage goes with the square of the
    /// scale, so the sum is of squares.
    /// </remarks>
    [Fact]
    public void TheRevealedCoverageMatchesTheBlades()
    {
      foreach (var total in Totals)
      {
        var step = GrassLod.RevealStep(total);

        for (var k = 0; k <= 120; k++)
        {
          var front = GrassLod.RevealFront(k / 120.0f, Span);
          var drawn = Drawn(total, front);

          var summed = 0.0f;
          for (var index = 0; index < drawn; index++)
          {
            var grown = GrassLod.Reveal(index, step, front, Span);
            summed += grown * grown;
          }

          // A hundredth of a cell. The sum steps one instance at a time where the closed form integrates, so
          // it cannot be exact - but a wrong antiderivative is out by about 2/3 of the span, which is four
          // times this.
          var closed = GrassLod.RevealedFraction(front, Span);
          Assert.InRange(summed / total, closed - 0.01f, closed + 0.01f);
        }
      }
    }

    /// <summary>The revealed fraction never steps, at either of the joins in its closed form.</summary>
    [Fact]
    public void TheRevealedFractionIsContinuousAcrossItsBranches()
    {
      var previous = 0.0f;

      for (var k = 0; k <= 4000; k++)
      {
        var front = k * (1.0f + Span) / 4000.0f;
        var revealed = GrassLod.RevealedFraction(front, Span);

        Assert.True(revealed >= previous - 0.0001f, $"revealed coverage fell at a front of {front}");
        Assert.True(revealed - previous < 0.01f, $"revealed coverage stepped at a front of {front}");

        previous = revealed;
      }

      Assert.Equal(1.0f, GrassLod.RevealedFraction(GrassLod.RevealFront(1.0f, Span), Span), 3);
    }

    /// <summary>
    /// Coverage is still held once the blades ramp in.
    /// </summary>
    /// <remarks>
    /// <see cref="CoverageIsHeldWhileTheScaleHasHeadroom"/> restated through the ramp, and the reason
    /// <see cref="GrassLod.RevealedFraction"/> exists at all: fed the raw fraction instead, the compensation
    /// would be answering a question about a cell whose blades are all full size, which is no longer the cell
    /// being drawn. That test would go on passing while the field it pins had gone thin.
    /// </remarks>
    [Fact]
    public void CoverageIsStillHeldOnceTheBladesRampIn()
    {
      const int Total = 1000;
      var step = GrassLod.RevealStep(Total);

      foreach (var fraction in new[] { 0.3f, 0.5f, 0.7f, 0.9f })
      {
        var front = GrassLod.RevealFront(fraction, Span);
        var revealed = GrassLod.RevealedFraction(front, Span);
        var scale = GrassLod.CoverageScale(revealed, 1.0f, 2.5f);

        var covered = 0.0f;
        for (var index = 0; index < Drawn(Total, front); index++)
        {
          var grown = GrassLod.Reveal(index, step, front, Span) * scale;
          covered += grown * grown;
        }

        // The identity: count times footprint holds, so the thinned cell still hides its ground. Within a
        // few percent, because the compensation multiplies the sum's own discretisation error by its square.
        Assert.InRange(covered, Total * 0.97f, Total * 1.03f);
      }
    }

    /// <summary>Appearing rises to one, arrives exactly, and stops there.</summary>
    /// <remarks>
    /// Arriving is the point rather than a detail - a cell fading out is freed when it reaches nought, so a
    /// curve that only approached its target would leave the node in the scene for good.
    /// </remarks>
    [Fact]
    public void AppearRisesToOneAndStops()
    {
      var appear = 0.0f;

      for (var frame = 0; frame < 60; frame++)
      {
        appear = GrassLod.Appear(appear, 1.0f, 1.0f / 60.0f, 0.4f);
      }

      Assert.Equal(1.0f, appear);
      Assert.Equal(1.0f, GrassLod.Appear(appear, 1.0f, 1.0f / 60.0f, 0.4f));

      var vanishing = 1.0f;
      for (var frame = 0; frame < 60; frame++)
      {
        vanishing = GrassLod.Appear(vanishing, 0.0f, 1.0f / 60.0f, 0.4f);
      }

      Assert.Equal(0.0f, vanishing);
    }

    /// <summary>Zero seconds is the fade turned off, and lands on the target at once.</summary>
    [Fact]
    public void ZeroSecondsIsTheFadeTurnedOff()
    {
      Assert.Equal(1.0f, GrassLod.Appear(0.0f, 1.0f, 1.0f / 60.0f, 0.0f));
      Assert.Equal(0.0f, GrassLod.Appear(1.0f, 0.0f, 1.0f / 60.0f, 0.0f));
    }
  }
}
