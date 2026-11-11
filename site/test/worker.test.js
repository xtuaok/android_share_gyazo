// Run: cd site && node --test   (Node.js 20+; no dependencies)
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import worker, { callback, sha256Base64Url, token, PREFIX } from "../src/worker.js";

const env = {
  GYAZO_CLIENT_ID: "client-id",
  GYAZO_CLIENT_SECRET: "client-secret",
  GYAZO_REDIRECT_URI: "https://example.test/ShareGyazo/oauth/gyazo/callback",
};
const verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
const challenge = createHash("sha256").update(verifier).digest("base64url");
const state = `${challenge}.nonce123`;

const post = (body) =>
  new Request(`https://example.test${PREFIX}/token`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });

test("sha256Base64Url matches RFC 7636 S256", async () => {
  assert.equal(await sha256Base64Url(verifier), challenge);
  // Example from RFC 7636 Appendix B.
  assert.equal(challenge, "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
});

test("token exchanges the code when the verifier matches", async () => {
  let sent;
  const fakeFetch = async (url, init) => {
    sent = { url, body: Object.fromEntries(new URLSearchParams(init.body)) };
    return Response.json({ access_token: "gyazo-token", token_type: "bearer" });
  };
  const response = await token(post({ code: "abc", state, verifier }), env, fakeFetch);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { access_token: "gyazo-token" });
  assert.equal(sent.url, "https://gyazo.com/oauth/token");
  assert.deepEqual(sent.body, {
    client_id: "client-id",
    client_secret: "client-secret",
    redirect_uri: env.GYAZO_REDIRECT_URI,
    code: "abc",
    grant_type: "authorization_code",
  });
});

test("token rejects a wrong verifier without calling Gyazo", async () => {
  let called = false;
  const fakeFetch = async () => {
    called = true;
    return Response.json({});
  };
  const response = await token(post({ code: "abc", state, verifier: "wrong-verifier" }), env, fakeFetch);
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { error: "invalid_verifier" });
  assert.equal(called, false);
});

test("token rejects malformed input", async () => {
  for (const body of ["not json", { code: "abc" }, { code: "a b", state, verifier }]) {
    const response = await token(post(body), env, async () => Response.json({}));
    assert.equal(response.status, 400);
  }
});

test("token reports Gyazo errors without leaking the secret", async () => {
  const fakeFetch = async () => Response.json({ error: "invalid_grant" }, { status: 400 });
  const response = await token(post({ code: "abc", state, verifier }), env, fakeFetch);
  assert.equal(response.status, 502);
  const text = await response.text();
  assert.match(text, /invalid_grant/);
  assert.doesNotMatch(text, /client-secret/);
});

test("token fails closed when the secret is not configured", async () => {
  const response = await token(post({ code: "abc", state, verifier }), { ...env, GYAZO_CLIENT_SECRET: "" });
  assert.equal(response.status, 500);
});

test("callback hands code and state to the app", async () => {
  const response = callback(new URL(`https://example.test${PREFIX}/callback?code=abc&state=${state}&x=<y>`));
  const html = await response.text();
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.match(html, /intent:\/\/oauth\/gyazo\?code=abc&#38;state=/);
  assert.match(html, /scheme=sharegyazo;package=xtuaok\.sharegyazo;end/);
  assert.doesNotMatch(html, /<y>/);
});

test("callback cannot inject markup through parameters", async () => {
  const evil = encodeURIComponent('"></a><script>alert(1)</script>');
  const response = callback(new URL(`https://example.test${PREFIX}/callback?code=${evil}&state=s`));
  const html = await response.text();
  assert.equal((html.match(/<script>/g) ?? []).length, 1);
  assert.doesNotMatch(html, /alert\(1\)<\/script>/);
});

test("other paths are served from static assets", async () => {
  const assets = { fetch: async (req) => new Response(`asset:${new URL(req.url).pathname}`) };
  const response = await worker.fetch(new Request("https://example.test/ShareGyazo/"), { ...env, ASSETS: assets });
  assert.equal(await response.text(), "asset:/ShareGyazo/");
});
