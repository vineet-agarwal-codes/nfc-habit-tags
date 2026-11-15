// Subscribes to catalog changes over Realtime as a client would, touches a published recipe,
// and exits 0 if the change arrives within 10 s.
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { homedir } from "node:os";

const env = Object.fromEntries(
  readFileSync(`${homedir()}/.config/habit-tags/supabase.env`, "utf8")
    .split("\n").filter((l) => l.includes("=")).map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
);
const host = new URL(env.SUPABASE_URL).host;
const ws = new WebSocket(`wss://${host}/realtime/v1/websocket?apikey=${env.SUPABASE_PUBLISHABLE_KEY}&vsn=1.0.0`);
const fail = (why) => { console.error(`realtime smoke FAILED: ${why}`); process.exit(1); };
setTimeout(() => fail("no change within 10 s of joining"), 15000);

ws.onerror = (e) => fail(`socket error ${e.message ?? ""}`);
ws.onopen = () => {
  ws.send(JSON.stringify({
    topic: "realtime:catalog", event: "phx_join", ref: "1",
    payload: { config: { postgres_changes: [
      { event: "*", schema: "public", table: "recipes" },
      { event: "*", schema: "public", table: "app_groups" },
    ] } },
  }));
};
let touched = false;
ws.onmessage = (m) => {
  const msg = JSON.parse(m.data);
  if (msg.event === "phx_reply" && msg.ref === "1") {
    if (msg.payload.status !== "ok") fail(`join refused: ${JSON.stringify(msg.payload)}`);
  }
  if (msg.event === "system" && msg.payload?.extension === "postgres_changes" && !touched) {
    if (msg.payload.status !== "ok") fail(`subscription failed: ${JSON.stringify(msg.payload)}`);
    touched = true;
    execFileSync(new URL("./sql.sh", import.meta.url).pathname, ["-"], {
      input: "update public.recipes set updated_at = now() where id = 'front-door';",
      stdio: ["pipe", "ignore", "inherit"],
    });
  }
  if (msg.event === "postgres_changes") {
    console.log(`realtime smoke OK: ${msg.payload.data?.type} on ${msg.payload.data?.table}`);
    process.exit(0);
  }
};
