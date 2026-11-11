// Run: cd site && node --test   (Node.js 20+; no dependencies)
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import vm from "node:vm";
import worker, { callback, refresh, sha256Base64Url, token, PREFIX } from "../src/worker.js";

const env = {
  GYAZO_CLIENT_ID: "gyazo-id",
  GYAZO_CLIENT_SECRET: "gyazo-secret",
  GYAZO_REDIRECT_URI: "https://example.test/ShareGyazo/oauth/gyazo/callback",
  IMGUR_CLIENT_ID: "imgur-id",
  IMGUR_CLIENT_SECRET: "imgur-secret",
};
const verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
const challenge = createHash("sha256").update(verifier).digest("base64url");
const state = `${challenge}.nonce123`;

const post = (path, body) =>
  new Request(`https://example.test${PREFIX}/${path}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });

function recordingFetch(response) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, body: Object.fromEntries(new URLSearchParams(init.body)) });
    return typeof response === "function" ? response() : Response.json(response);
  };
  fn.calls = calls;
  return fn;
}

test("sha256Base64Url matches RFC 7636 S256", async () => {
  assert.equal(await sha256Base64Url(verifier), challenge);
  assert.equal(challenge, "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"); // RFC 7636 Appendix B
});

test("gyazo: exchanges the code with the secret and redirect_uri", async () => {
  const fetchImpl = recordingFetch({ access_token: "gyazo-token", token_type: "bearer" });
  const response = await token("gyazo", post("gyazo/token", { code: "abc", state, verifier }), env, fetchImpl);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { access_token: "gyazo-token" });
  assert.equal(fetchImpl.calls[0].url, "https://gyazo.com/oauth/token");
  assert.deepEqual(fetchImpl.calls[0].body, {
    client_id: "gyazo-id",
    client_secret: "gyazo-secret",
    redirect_uri: env.GYAZO_REDIRECT_URI,
    code: "abc",
    grant_type: "authorization_code",
  });
});

test("imgur: refreshes a token", async () => {
  const fetchImpl = recordingFetch({ access_token: "new", refresh_token: "r2", expires_in: 3600 });
  const response = await refresh("imgur", post("imgur/refresh", { refresh_token: "r1" }), env, fetchImpl);
  assert.deepEqual(await response.json(), { access_token: "new", refresh_token: "r2", expires_in: 3600 });
  assert.deepEqual(fetchImpl.calls[0].body, {
    client_id: "imgur-id",
    client_secret: "imgur-secret",
    refresh_token: "r1",
    grant_type: "refresh_token",
  });
});

test("token rejects a wrong verifier without calling the service", async () => {
  const fetchImpl = recordingFetch({});
  const response = await token("gyazo", post("gyazo/token", { code: "abc", state, verifier: "wrong" }), env, fetchImpl);
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { error: "invalid_verifier" });
  assert.equal(fetchImpl.calls.length, 0);
});

test("token and refresh reject malformed input", async () => {
  for (const body of ["not json", { code: "abc" }, { code: "a b", state, verifier }]) {
    assert.equal((await token("gyazo", post("gyazo/token", body), env, recordingFetch({}))).status, 400);
  }
  for (const body of ["not json", {}, { refresh_token: "has space" }]) {
    assert.equal((await refresh("imgur", post("imgur/refresh", body), env, recordingFetch({}))).status, 400);
  }
});

test("service errors are reported without leaking the secret", async () => {
  const fetchImpl = recordingFetch(() => Response.json({ error: "invalid_grant" }, { status: 400 }));
  const response = await refresh("imgur", post("imgur/refresh", { refresh_token: "r1" }), env, fetchImpl);
  assert.equal(response.status, 400);
  const text = await response.text();
  assert.match(text, /invalid_grant/);
  assert.doesNotMatch(text, /secret/);
});

test("fails closed when the secret is not configured", async () => {
  const response = await refresh(
    "imgur", post("imgur/refresh", { refresh_token: "r1" }), { ...env, IMGUR_CLIENT_SECRET: "" }, recordingFetch({}),
  );
  assert.equal(response.status, 500);
  const gyazo = await token(
    "gyazo", post("gyazo/token", { code: "abc", state, verifier }), { ...env, GYAZO_CLIENT_SECRET: "" }, recordingFetch({}),
  );
  assert.equal(gyazo.status, 500);
});

/** Runs the callback page's script as a browser would and returns where it navigates. */
async function runCallbackPage(provider, { search = "", hash = "", userAgent = "Mozilla/5.0 (Linux; Android 16)" } = {}) {
  const response = callback(provider);
  const html = await response.text();
  const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
  const link = { href: "" };
  const result = { replaced: null, historyUrl: null, link, response, html };
  vm.runInNewContext(script, {
    URLSearchParams,
    encodeURIComponent,
    navigator: { userAgent },
    document: { getElementById: () => link },
    history: { replaceState: (_s, _t, url) => { result.historyUrl = url; } },
    location: {
      pathname: `${PREFIX}/${provider}/callback`,
      search,
      hash,
      replace: (url) => { result.replaced = url; },
    },
  });
  return result;
}

test("gyazo callback forwards code and state from the query to the app", async () => {
  const r = await runCallbackPage("gyazo", { search: `?code=abc&state=${state}&unrelated=1` });
  assert.equal(r.response.headers.get("cache-control"), "no-store");
  assert.equal(
    r.replaced,
    `intent://oauth/gyazo?code=abc&state=${state}#Intent;scheme=sharegyazo;package=xtuaok.sharegyazo;end`,
  );
  assert.equal(r.link.href, r.replaced);
});

test("imgur callback forwards tokens from the fragment and clears them from history", async () => {
  const r = await runCallbackPage("imgur", {
    hash: "#access_token=a1&expires_in=2419200&token_type=bearer&refresh_token=r1&account_username=x&state=st",
  });
  assert.equal(
    r.replaced,
    "intent://oauth/imgur?access_token=a1&expires_in=2419200&refresh_token=r1&state=st" +
      "#Intent;scheme=sharegyazo;package=xtuaok.sharegyazo;end",
  );
  assert.equal(r.historyUrl, `${PREFIX}/imgur/callback`);
});

test("callback uses the custom scheme outside Android", async () => {
  const r = await runCallbackPage("gyazo", { search: "?error=access_denied", userAgent: "Mozilla/5.0 (X11)" });
  assert.equal(r.replaced, "sharegyazo://oauth/gyazo?error=access_denied");
});

test("callback encodes values and never puts request data into the page", async () => {
  const evil = encodeURIComponent('"></a><script>alert(1)</script>');
  const r = await runCallbackPage("gyazo", { search: `?code=${evil}&state=s` });
  assert.equal((r.html.match(/<script>/g) ?? []).length, 1);
  assert.doesNotMatch(r.html, /alert/);
  assert.match(r.replaced, /code=%22%3E%3C%2Fa%3E%3Cscript%3Ealert/);
});

test("routing: unknown providers, gyazo refresh, imgur code exchange and other paths fall through to assets", async () => {
  const assets = { fetch: async (req) => new Response(`asset:${new URL(req.url).pathname}`) };
  const fullEnv = { ...env, ASSETS: assets };
  const cases = [
    new Request("https://example.test/ShareGyazo/"),
    new Request(`https://example.test${PREFIX}/evil/callback`),
    new Request(`https://example.test${PREFIX}/constructor/callback`),
    post("gyazo/refresh", { refresh_token: "r" }),
    post("imgur/token", { code: "abc", state, verifier }),
  ];
  for (const request of cases) {
    const text = await (await worker.fetch(request, fullEnv)).text();
    assert.match(text, /^asset:/);
  }
});
