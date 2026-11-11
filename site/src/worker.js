// Relay for the ShareGyazo Gyazo login.
//
// Gyazo requires the client secret to exchange an authorization code, so the exchange
// happens here and the secret never ships in the app. The app binds each login to a random
// verifier (PKCE-style): the OAuth state starts with base64url(SHA-256(verifier)), and
// /token only exchanges a code when it is given the verifier that matches. An intercepted
// code is useless without it.
//
// Nothing is stored and nothing is logged except errors.
//
// Environment:
//   GYAZO_CLIENT_ID      (var)    public client id
//   GYAZO_CLIENT_SECRET  (secret) set in the Cloudflare dashboard or with `wrangler secret put`
//   GYAZO_REDIRECT_URI   (var)    must match the Callback URL registered at Gyazo

export const PREFIX = "/ShareGyazo/oauth/gyazo";
const APP_CALLBACK = "sharegyazo://oauth/gyazo";
const APP_PACKAGE = "xtuaok.sharegyazo";
const GYAZO_TOKEN_URL = "https://gyazo.com/oauth/token";
const TOKEN_PATTERN = /^[A-Za-z0-9._~-]{1,512}$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === `${PREFIX}/callback` && request.method === "GET") {
      return callback(url);
    }
    if (url.pathname === `${PREFIX}/token` && request.method === "POST") {
      return token(request, env);
    }
    return env.ASSETS.fetch(request);
  },
};

/** Gyazo redirects here; hand code/state (or error) back to the app. */
export function callback(url) {
  const params = new URLSearchParams();
  for (const key of ["code", "state", "error"]) {
    const value = url.searchParams.get(key);
    if (value !== null) params.set(key, value);
  }
  const query = params.toString();
  const appUrl = `${APP_CALLBACK}?${query}`;
  // Chrome on Android only follows custom schemes from a user gesture or an intent: URL.
  const intentUrl =
    `intent://oauth/gyazo?${query}#Intent;scheme=sharegyazo;package=${APP_PACKAGE};end`;
  const html = `<!doctype html>
<html lang="ja"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="no-referrer">
<title>ShareGyazo</title>
<style>body{font-family:system-ui,sans-serif;margin:0;padding:48px 16px;text-align:center;line-height:1.7}
a{display:inline-block;margin-top:16px;padding:12px 24px;border-radius:24px;background:#1e6bd6;color:#fff;text-decoration:none}</style>
</head><body>
<p>ShareGyazo に戻ります…<br>Returning to ShareGyazo…</p>
<a id="open" href="${escapeHtml(intentUrl)}">アプリを開く / Open the app</a>
<script>
  var isAndroid = /Android/i.test(navigator.userAgent);
  location.replace(isAndroid ? ${JSON.stringify(intentUrl)} : ${JSON.stringify(appUrl)});
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

/** POST {code, state, verifier} -> {access_token} */
export async function token(request, env, fetchImpl = fetch) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "invalid_request" }, 400);
  }
  const { code, state, verifier } = body ?? {};
  if (![code, state, verifier].every((v) => typeof v === "string" && TOKEN_PATTERN.test(v))) {
    return json({ error: "invalid_request" }, 400);
  }
  const [challenge] = state.split(".");
  if (!timingSafeEqual(await sha256Base64Url(verifier), challenge)) {
    return json({ error: "invalid_verifier" }, 400);
  }
  if (!env.GYAZO_CLIENT_SECRET) {
    console.error("GYAZO_CLIENT_SECRET is not configured");
    return json({ error: "server_error" }, 500);
  }

  const response = await fetchImpl(GYAZO_TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: env.GYAZO_CLIENT_ID,
      client_secret: env.GYAZO_CLIENT_SECRET,
      redirect_uri: env.GYAZO_REDIRECT_URI,
      code,
      grant_type: "authorization_code",
    }),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || typeof result.access_token !== "string") {
    console.error("Gyazo token exchange failed", response.status, result.error ?? "");
    return json({ error: result.error ?? "exchange_failed" }, 502);
  }
  return json({ access_token: result.access_token });
}

export async function sha256Base64Url(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  let binary = "";
  for (const byte of new Uint8Array(digest)) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
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

function escapeHtml(text) {
  return text.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}
