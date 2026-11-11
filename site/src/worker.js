// Login relay for ShareGyazo (Gyazo and imgur).
//
// Calls that need a client secret happen here so the secrets never ship in the app:
//   - Gyazo: exchanging the authorization code (Gyazo only supports response_type=code).
//     The app binds each login to a random verifier (PKCE-style): the OAuth state starts
//     with base64url(SHA-256(verifier)), and /token only exchanges a code when it is given
//     the matching verifier, so an intercepted code is useless.
//   - imgur: refreshing a token. imgur logins use response_type=token (the only type imgur
//     still supports), which needs no secret; the tokens arrive in the URL fragment.
// /callback is the redirect URL registered at both services; it hands the result to the app.
//
// Nothing is stored and nothing is logged except errors.
//
// Environment (vars in wrangler.jsonc, secrets in the Cloudflare dashboard):
//   GYAZO_CLIENT_ID, GYAZO_REDIRECT_URI, GYAZO_CLIENT_SECRET (secret)
//   IMGUR_CLIENT_ID, IMGUR_CLIENT_SECRET (secret)

export const PREFIX = "/ShareGyazo/oauth";
const APP_SCHEME = "sharegyazo";
const APP_PACKAGE = "xtuaok.sharegyazo";
const TOKEN_PATTERN = /^[A-Za-z0-9._~-]{1,512}$/;

export const PROVIDERS = {
  gyazo: {
    tokenUrl: "https://gyazo.com/oauth/token",
    credentials: (env) => ({
      client_id: env.GYAZO_CLIENT_ID,
      client_secret: env.GYAZO_CLIENT_SECRET,
      redirect_uri: env.GYAZO_REDIRECT_URI,
    }),
    canExchange: true,
    canRefresh: false,
  },
  imgur: {
    tokenUrl: "https://api.imgur.com/oauth2/token",
    credentials: (env) => ({
      client_id: env.IMGUR_CLIENT_ID,
      client_secret: env.IMGUR_CLIENT_SECRET,
    }),
    canExchange: false,
    canRefresh: true,
  },
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const match = url.pathname.match(/^\/ShareGyazo\/oauth\/([a-z]+)\/([a-z]+)$/);
    const provider = match && Object.hasOwn(PROVIDERS, match[1]) ? match[1] : null;
    if (provider) {
      const action = match[2];
      if (action === "callback" && request.method === "GET") return callback(provider);
      if (action === "token" && request.method === "POST" && PROVIDERS[provider].canExchange) {
        return token(provider, request, env);
      }
      if (action === "refresh" && request.method === "POST" && PROVIDERS[provider].canRefresh) {
        return refresh(provider, request, env);
      }
    }
    return env.ASSETS.fetch(request);
  },
};

// Parameters handed to the app: from the query (code flow) or the fragment (token flow).
export const CALLBACK_KEYS = [
  "code", "state", "error", "access_token", "refresh_token", "expires_in", "account_username",
];

/**
 * The service redirects here after login. The page forwards the result to the app with an
 * intent: URL (Chrome on Android) or the sharegyazo:// scheme. It is built in the browser
 * because the token flow puts the tokens in the fragment, which never reaches the server;
 * nothing from the request is interpolated into the page.
 */
export function callback(provider) {
  const html = `<!doctype html>
<html lang="ja"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="no-referrer">
<title>ShareGyazo</title>
<style>body{font-family:system-ui,sans-serif;margin:0;padding:48px 16px;text-align:center;line-height:1.7}
a{display:inline-block;margin-top:16px;padding:12px 24px;border-radius:24px;background:#1e6bd6;color:#fff;text-decoration:none}</style>
</head><body>
<p>ShareGyazo に戻ります…<br>Returning to ShareGyazo…</p>
<a id="open" href="#">アプリを開く / Open the app</a>
<script>
(function () {
  var keys = ${JSON.stringify(CALLBACK_KEYS)};
  var found = {};
  [location.search.substring(1), location.hash.substring(1)].forEach(function (part) {
    new URLSearchParams(part).forEach(function (value, key) {
      if (keys.indexOf(key) >= 0) found[key] = value;
    });
  });
  var query = Object.keys(found).map(function (key) {
    return encodeURIComponent(key) + "=" + encodeURIComponent(found[key]);
  }).join("&");
  var path = "oauth/${provider}?" + query;
  var target = /Android/i.test(navigator.userAgent)
    ? "intent://" + path + "#Intent;scheme=${APP_SCHEME};package=${APP_PACKAGE};end"
    : "${APP_SCHEME}://" + path;
  document.getElementById("open").href = target;
  // Drop the tokens from the address bar and history before leaving.
  history.replaceState(null, "", location.pathname);
  location.replace(target);
})();
</script>
</body></html>`;
  return new Response(html, {
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "referrer-policy": "no-referrer",
    },
  });
}

/** POST {code, state, verifier} -> {access_token, refresh_token?, expires_in?} */
export async function token(provider, request, env, fetchImpl = fetch) {
  const body = await readJson(request);
  const { code, state, verifier } = body ?? {};
  if (![code, state, verifier].every(isToken)) return json({ error: "invalid_request" }, 400);
  const [challenge] = state.split(".");
  if (!timingSafeEqual(await sha256Base64Url(verifier), challenge)) {
    return json({ error: "invalid_verifier" }, 400);
  }
  return exchange(provider, env, { code, grant_type: "authorization_code" }, fetchImpl);
}

/** POST {refresh_token} -> {access_token, refresh_token?, expires_in?} (imgur) */
export async function refresh(provider, request, env, fetchImpl = fetch) {
  const body = await readJson(request);
  if (!isToken(body?.refresh_token)) return json({ error: "invalid_request" }, 400);
  return exchange(provider, env, { refresh_token: body.refresh_token, grant_type: "refresh_token" }, fetchImpl);
}

async function exchange(provider, env, grant, fetchImpl) {
  const config = PROVIDERS[provider];
  const credentials = config.credentials(env);
  if (!credentials.client_id || !credentials.client_secret) {
    console.error(`${provider}: client id or secret is not configured`);
    return json({ error: "server_error" }, 500);
  }
  const response = await fetchImpl(config.tokenUrl, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ ...credentials, ...grant }),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || typeof result.access_token !== "string") {
    const error = typeof result.error === "string" ? result.error : "exchange_failed";
    console.error(`${provider} token request failed`, response.status, error);
    return json({ error }, response.status === 400 ? 400 : 502);
  }
  // Pass through only what the app needs.
  const out = { access_token: result.access_token };
  if (typeof result.refresh_token === "string") out.refresh_token = result.refresh_token;
  if (Number.isFinite(result.expires_in)) out.expires_in = result.expires_in;
  return json(out);
}

export async function sha256Base64Url(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  let binary = "";
  for (const byte of new Uint8Array(digest)) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function readJson(request) {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

function isToken(value) {
  return typeof value === "string" && TOKEN_PATTERN.test(value);
}

function timingSafeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}
