using System;
using BestiaBehemothClient.Game.UI.BugReport;
using Xunit;

namespace BestiaClient.Tests
{
  public class CrashSignatureTest
  {
    [Fact]
    public void TheSameThrowSiteGivesTheSameSignatureWhateverTheMessage()
    {
      var first = CaughtFromFail("entity 12 not found");
      var second = CaughtFromFail("entity 9000 not found");

      Assert.Equal(CrashSignature.Of(first), CrashSignature.Of(second));
      Assert.Matches("^[0-9a-f]{64}$", CrashSignature.Of(first));
    }

    [Fact]
    public void AnotherThrowSiteOrTypeGivesAnotherSignature()
    {
      var here = CaughtFromFail("x");
      var elsewhere = Caught(() => FailElsewhere("x"));
      var otherType = Caught(() => throw new ArgumentException("x"));

      Assert.NotEqual(CrashSignature.Of(here), CrashSignature.Of(elsewhere));
      Assert.NotEqual(CrashSignature.Of(here), CrashSignature.Of(otherType));
    }

    private static void Fail(string message)
    {
      throw new InvalidOperationException(message);
    }

    private static void FailElsewhere(string message)
    {
      throw new InvalidOperationException(message);
    }

    /// Always the same call path, unlike a lambda per test, whose compiler-made name is part of the stack.
    private static Exception CaughtFromFail(string message)
    {
      return Caught(() => Fail(message));
    }

    private static Exception Caught(Action action)
    {
      try
      {
        action();
      }
      catch (Exception e)
      {
        return e;
      }

      throw new InvalidOperationException("nothing was thrown");
    }
  }
}
