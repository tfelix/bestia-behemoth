using System;
using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace BestiaBehemothClient.Auth
{
  /// <summary>
  /// The calls the game makes against the login server. The passkey ceremony between /start and
  /// /exchange happens in the system browser; /refresh replaces all of it on later starts.
  /// </summary>
  public sealed class AuthApiClient : IDisposable
  {
    private readonly HttpClient _http;

    public AuthApiClient(string baseUrl)
    {
      _http = new HttpClient
      {
        BaseAddress = new Uri(baseUrl.TrimEnd('/') + "/"),
        Timeout = TimeSpan.FromSeconds(10)
      };
    }

    /// <summary>What a successful sign-in yields, however it was reached.</summary>
    public sealed class Tokens
    {
      /// <summary>Short-lived JWT for the zone handshake.</summary>
      public string Token { get; init; }

      /// <summary>The standing session, to be stored in place of whatever it replaced.</summary>
      public string RefreshToken { get; init; }
    }

    /// <summary>
    /// Always asks for the sign-in page. That page offers creating an account and recovering one as
    /// well, so the game has no reason to ask for the REGISTER intent the server still accepts.
    /// </summary>
    public async Task<StartResponse> StartAsync(
      string redirectUri,
      string codeChallenge,
      string state,
      CancellationToken cancellationToken)
    {
      var request = new StartRequest
      {
        RedirectUri = redirectUri,
        CodeChallenge = codeChallenge,
        CodeChallengeMethod = "S256",
        State = state,
        Intent = "LOGIN"
      };

      using var response = await _http
        .PostAsJsonAsync("api/v1/auth/game/start", request, cancellationToken)
        .ConfigureAwait(false);

      response.EnsureSuccessStatusCode();

      return await response.Content
        .ReadFromJsonAsync<StartResponse>(cancellationToken)
        .ConfigureAwait(false);
    }

    /// <summary>
    /// Trades the single-use code for the zone JWT, plus the token that makes the next start silent.
    /// </summary>
    public async Task<Tokens> ExchangeAsync(
      string code,
      string codeVerifier,
      CancellationToken cancellationToken)
    {
      var request = new ExchangeRequest
      {
        Code = code,
        CodeVerifier = codeVerifier
      };

      using var response = await _http
        .PostAsJsonAsync("api/v1/auth/game/exchange", request, cancellationToken)
        .ConfigureAwait(false);

      response.EnsureSuccessStatusCode();

      return await ReadTokensAsync(response, cancellationToken).ConfigureAwait(false);
    }

    /// <summary>
    /// Resumes a stored session, or returns null if the server will not have it.
    /// </summary>
    /// <remarks>
    /// A refusal is an ordinary outcome, not an error: the token expires, and the server withdraws it
    /// on a ban, on account recovery, and if it ever sees the same one twice. All the caller can do
    /// with any of those is throw the token away and open the browser, so they are not distinguished.
    /// A transport failure still throws - that one is worth telling the player about.
    /// </remarks>
    public async Task<Tokens> RefreshAsync(string refreshToken, CancellationToken cancellationToken)
    {
      var request = new RefreshRequest
      {
        RefreshToken = refreshToken
      };

      using var response = await _http
        .PostAsJsonAsync("api/v1/auth/game/refresh", request, cancellationToken)
        .ConfigureAwait(false);

      if ((int)response.StatusCode >= 400 && (int)response.StatusCode < 500)
      {
        return null;
      }

      response.EnsureSuccessStatusCode();

      return await ReadTokensAsync(response, cancellationToken).ConfigureAwait(false);
    }

    /// <summary>
    /// Ends the stored session on the server. Best effort: a client that cannot reach the login server
    /// still forgets its own copy, which is the part the player can see.
    /// </summary>
    public async Task RevokeAsync(string refreshToken, CancellationToken cancellationToken)
    {
      var request = new RevokeRequest
      {
        RefreshToken = refreshToken
      };

      using var response = await _http
        .PostAsJsonAsync("api/v1/auth/game/revoke", request, cancellationToken)
        .ConfigureAwait(false);

      response.EnsureSuccessStatusCode();
    }

    private static async Task<Tokens> ReadTokensAsync(
      HttpResponseMessage response,
      CancellationToken cancellationToken)
    {
      var body = await response.Content
        .ReadFromJsonAsync<TokenResponse>(cancellationToken)
        .ConfigureAwait(false);

      if (string.IsNullOrEmpty(body?.Token))
      {
        return null;
      }

      return new Tokens
      {
        Token = body.Token,
        RefreshToken = body.RefreshToken
      };
    }

    public void Dispose()
    {
      _http.Dispose();
    }

    private sealed class StartRequest
    {
      [JsonPropertyName("redirect_uri")]
      public string RedirectUri { get; set; }

      [JsonPropertyName("code_challenge")]
      public string CodeChallenge { get; set; }

      [JsonPropertyName("code_challenge_method")]
      public string CodeChallengeMethod { get; set; }

      [JsonPropertyName("state")]
      public string State { get; set; }

      [JsonPropertyName("intent")]
      public string Intent { get; set; }
    }

    public sealed class StartResponse
    {
      [JsonPropertyName("session_id")]
      public string SessionId { get; set; }

      [JsonPropertyName("login_url")]
      public string LoginUrl { get; set; }

      [JsonPropertyName("expires_in")]
      public long ExpiresIn { get; set; }
    }

    private sealed class ExchangeRequest
    {
      [JsonPropertyName("code")]
      public string Code { get; set; }

      [JsonPropertyName("code_verifier")]
      public string CodeVerifier { get; set; }
    }

    private sealed class RefreshRequest
    {
      [JsonPropertyName("refresh_token")]
      public string RefreshToken { get; set; }
    }

    private sealed class RevokeRequest
    {
      [JsonPropertyName("refresh_token")]
      public string RefreshToken { get; set; }
    }

    /// <summary>The body /exchange and /refresh share.</summary>
    private sealed class TokenResponse
    {
      [JsonPropertyName("token")]
      public string Token { get; set; }

      [JsonPropertyName("refresh_token")]
      public string RefreshToken { get; set; }
    }
  }
}
