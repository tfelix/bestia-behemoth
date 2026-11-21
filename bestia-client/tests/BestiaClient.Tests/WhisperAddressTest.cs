using BestiaBehemothClient.Game.UI.Chat;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  public class WhisperAddressTest
  {
    [Theory]
    [InlineData("Bob hi", "Bob", "hi")]
    [InlineData("  Bob   hi there  ", "Bob", "hi there")]
    [InlineData("\"Åsa Lind\" hi there", "Åsa Lind", "hi there")]
    [InlineData("\"Bob\"hi", "Bob", "hi")]
    public void ALineIsSplitIntoNameAndText(string afterPrefix, string target, string text)
    {
      Assert.Equal((target, text), WhisperAddress.Split(afterPrefix));
    }

    /// <summary>A name alone only chooses who the next lines go to.</summary>
    [Theory]
    [InlineData("Bob", "Bob")]
    [InlineData("\"Åsa Lind\"", "Åsa Lind")]
    public void ANameAloneHasNoText(string afterPrefix, string target)
    {
      Assert.Equal((target, ""), WhisperAddress.Split(afterPrefix));
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("\"Åsa Lind hi")]
    [InlineData("\"\" hi")]
    public void ALineThatNamesNobodyHasAnEmptyName(string afterPrefix)
    {
      Assert.Equal("", WhisperAddress.Split(afterPrefix).Target);
    }
  }
}
