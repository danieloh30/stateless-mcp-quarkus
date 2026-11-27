"use strict";
const $ = (s) => document.querySelector(s);
function applyTheme(dark) {
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  $("#theme").setAttribute("aria-pressed", String(dark));
  $("#theme").textContent = dark ? "Light theme" : "Dark theme";
}
applyTheme(localStorage.getItem("helios-theme") === "dark");
$("#theme").addEventListener("click", () => {
  const dark = document.documentElement.dataset.theme !== "dark";
  applyTheme(dark);
  localStorage.setItem("helios-theme", dark ? "dark" : "light");
});
const presets = {
  getShipmentStatus: {
    title: "Track a shipment",
    args: { trackingId: "HLX-10032291" },
  },
  getWarehouseInventory: {
    title: "Check inventory",
    args: { sku: "SKU-COLD-4521" },
  },
  estimateDelivery: {
    title: "Estimate a lane",
    args: { originHub: "FRA", destinationHub: "YYZ", weightKg: 620 },
  },
  getCarrierSla: { title: "Carrier SLA", args: { carrierId: "HELIOS-AIR" } },
  listOpenExceptions: { title: "Open exceptions", args: { region: "APAC" } },
};
const questions = [
  "Where is shipment HLX-10032291 and will it be on time?",
  "How much stock of SKU-COLD-4521 is available?",
  "What open exceptions need attention in APAC?",
];
let runtime = null,
  tools = [],
  busy = false,
  asking = false,
  paused = false,
  timer = null,
  calls = [],
  lastCall = null,
  activationMs = null,
  runGeneration = 0;
let observedAt = 0;
const modes = {
  dev: [
    "Local dev · single instance",
    "One MCP server. Protocol and agent tracing work here; multi-replica repetition is reserved for your cluster stages.",
  ],
  compose: [
    "Local cluster · five configured replicas",
    "Repeat the selected business lookup through nginx. DNS is an address estimate; distinct serving replicas are counted from actual calls.",
  ],
  openshift: [
    "OpenShift · fixed fleet",
    "Repeat the same lookup across the deployed fleet. Readiness is observed passively; no pod controls are exposed by this SPA.",
  ],
  knative: [
    "OpenShift · Knative serverless",
    "Only explicit discovery or tool invocation should activate the fleet. Passive observation reads platform metadata. Zero readiness does not by itself prove zero running pods; confirm the pod count in OpenShift.",
  ],
};
function node(tag, text, cls) {
  const el = document.createElement(tag);
  if (text !== undefined) el.textContent = String(text);
  if (cls) el.className = cls;
  return el;
}
function pretty(value) {
  return JSON.stringify(value, null, 2);
}
function status(id, text, error = false) {
  $(id).textContent = text;
  $(id).className = error ? "error" : "";
}
async function json(url, body, timeout = 35000) {
  const controller = new AbortController(),
    stop = setTimeout(() => controller.abort(), timeout);
  try {
    const res = await fetch(url, {
      method: body === undefined ? "GET" : "POST",
      headers: body === undefined ? {} : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
    const text = await res.text();
    let data;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      throw Error(`HTTP ${res.status}: non-JSON response from the service`);
    }
    if (!res.ok) throw Error(data?.error || `HTTP ${res.status}`);
    return data;
  } catch (e) {
    if (e.name === "AbortError")
      throw Error(
        "Request deadline exceeded. Check the service before retrying.",
      );
    throw e;
  } finally {
    clearTimeout(stop);
  }
}
function tab(name) {
  document
    .querySelectorAll("[data-view]")
    .forEach((el) => (el.hidden = el.dataset.view !== name));
  document
    .querySelectorAll("[data-tab]")
    .forEach((el) =>
      el.setAttribute("aria-selected", String(el.dataset.tab === name)),
    );
}
document
  .querySelectorAll("[data-tab]")
  .forEach((el) => el.addEventListener("click", () => tab(el.dataset.tab)));
function lock() {
  $("#run").disabled = busy || !tools.length;
  $("#repeat").disabled =
    busy || !tools.length || runtime?.burstEnabled !== true;
  $("#tool").disabled = busy;
  $("#invalid").disabled = busy || !tools.length;
  $("#discover").disabled = busy;
}
function reset() {
  window.HeliosFlow.reset();
  calls = [];
  activationMs = null;
  $("#calls").replaceChildren();
  $("#observedCount").textContent = "0";
  $("#consistency").textContent = "Not measured";
  $("#activation").textContent = "";
  status("#runStatus", "");
}
$("#reset").addEventListener("click", () => {
  if (busy || asking) return;
  reset();
  lastCall = null;
  $("#traceSteps").replaceChildren();
  $("#trace").hidden = true;
  $("#answer").hidden = true;
  $("#resultPanel").hidden = true;
  for (const id of ["headers", "request", "response"])
    $("#" + id).textContent = "No evidence collected";
  status("#protocolStatus", "Run a business tool to collect evidence.");
});
$("#presentation").addEventListener("click", () => {
  const on = document.body.classList.toggle("presentation");
  $("#presentation").setAttribute("aria-pressed", String(on));
  localStorage.setItem("helios-presentation", String(on));
});
if (localStorage.getItem("helios-presentation") === "true") {
  document.body.classList.add("presentation");
  $("#presentation").setAttribute("aria-pressed", "true");
}
function schedule() {
  clearTimeout(timer);
  if (!paused) timer = setTimeout(observe, 3000);
}
async function observe() {
  try {
    const next = await json("agent/runtime", undefined, 5000);
    const changed = runtime && runtime.environment !== next.environment;
    runtime = next;
    window.HeliosFlow.runtime(next);
    observedAt = Date.now();
    if (changed) {
      runGeneration++;
      reset();
      status(
        "#runStatus",
        "Environment changed. Start a new run for this stage.",
      );
    }
    const mode = modes[next.environment] || [
      "Environment unavailable",
      "Verify deployment configuration.",
    ];
    $("#environment").textContent = mode[0];
    $("#environmentDescription").textContent = mode[1];
    $("#fleetExplanation").textContent = mode[1];
    $("#readyCount").textContent =
      next.readyReplicas === null ? "Unknown" : next.readyReplicas;
    $("#readiness").textContent =
      `Ready replicas: ${next.readyReplicas === null ? "unknown" : next.readyReplicas} · ${next.readinessSource}`;
    $("#observation").textContent =
      `Observed ${new Date(observedAt).toLocaleTimeString()}. Observation does not invoke MCP tools.`;
    $("#discover").textContent = next.scaleToZero
      ? "Discover tools (may activate fleet)"
      : "Discover tools";
    lock();
  } catch (e) {
    status("#readiness", "Readiness unknown: " + e.message, true);
    $("#readyCount").textContent = "Unknown";
    runtime = null;
    window.HeliosFlow.runtime(null);
    lock();
  } finally {
    schedule();
  }
}
$("#pause").addEventListener("click", () => {
  paused = !paused;
  $("#pause").setAttribute("aria-pressed", String(paused));
  $("#pause").textContent = paused ? "Resume observation" : "Pause observation";
  clearTimeout(timer);
  if (!paused) observe();
  else
    $("#observation").textContent =
      "Observation paused. Counts above are the last observation; no background polling is running.";
});
async function discover() {
  if (busy) return;
  busy = true;
  lock();
  status("#catalogStatus", "Discovering the live tool catalog…");
  window.HeliosFlow.begin("discover");
  try {
    tools = await json("agent/catalog");
    $("#tool").replaceChildren(
      ...tools.map((t) => {
        const o = node("option", presets[t.name]?.title || t.name);
        o.value = t.name;
        return o;
      }),
    );
    select();
    status("#catalogStatus", `${tools.length} business tools discovered.`);
  } catch (e) {
    status("#catalogStatus", e.message, true);
  } finally {
    busy = false;
    window.HeliosFlow.idle();
    lock();
  }
}
function select() {
  const t = tools.find((t) => t.name === $("#tool").value);
  $("#arguments").replaceChildren();
  if (!t) return;
  $("#description").textContent = t.description || "";
  for (const key of t.params || []) {
    const group = node("div"),
      input = node("input");
    input.id = "arg-" + key;
    input.dataset.arg = key;
    input.value = presets[t.name]?.args[key] ?? "";
    input.dataset.numeric = String(
      typeof presets[t.name]?.args[key] === "number",
    );
    if (input.dataset.numeric === "true") {
      input.type = "number";
      input.step = "any";
    }
    const label = node("label", key);
    label.htmlFor = input.id;
    group.append(label, input);
    $("#arguments").append(group);
  }
}
$("#discover").addEventListener("click", discover);
$("#tool").addEventListener("change", select);
function args() {
  return Object.fromEntries(
    [...document.querySelectorAll("[data-arg]")].map((el) => [
      el.dataset.arg,
      el.dataset.numeric === "true" ? Number(el.value) : el.value,
    ]),
  );
}
function protocol(call) {
  lastCall = call;
  const e = call.evidence || {};
  $("#headers").textContent = pretty(e.requestHeaders || {});
  $("#request").textContent = pretty(e.request || {});
  $("#response").textContent = pretty({
    isError: call.isError,
    result: call.result,
  });
  const version = e.requestHeaders?.["MCP-Protocol-Version"];
  status(
    "#protocolStatus",
    version
      ? `Observed protocol ${version} · ${e.transientConnection ? "transient connection" : "connection is not reported transient"} · ${e.instanceId || "replica unknown"}`
      : "This result contains no server evidence. No protocol claim has been inferred.",
    !version,
  );
}
function canonical(value) {
  if (Array.isArray(value)) return "[" + value.map(canonical).join(",") + "]";
  if (value && typeof value === "object")
    return (
      "{" +
      Object.keys(value)
        .sort()
        .map((k) => JSON.stringify(k) + ":" + canonical(value[k]))
        .join(",") +
      "}"
    );
  return JSON.stringify(value);
}
function showCall(call, elapsed) {
  window.HeliosFlow.complete(call);
  calls.push(call);
  protocol(call);
  const e = call.evidence || {},
    article = node("article", undefined, "call");
  article.append(
    node(
      "h3",
      `#${calls.length} · ${call.tool} · ${call.isError ? "Tool error" : "Success"}`,
      call.isError ? "error" : "ok",
    ),
    node(
      "p",
      `Serving replica: ${e.instanceId || "unknown"} · ${Math.round(elapsed)} ms end-to-end · ${call.durationMs} ms agent → fleet`,
    ),
  );
  $("#calls").append(article);
  const ids = new Set(calls.map((c) => c.evidence?.instanceId).filter(Boolean));
  $("#observedCount").textContent = ids.size;
  const good = calls.filter((c) => !c.isError);
  const same =
    good.length > 1 &&
    good.every((c) => canonical(c.result) === canonical(good[0].result));
  $("#consistency").textContent = calls.some((c) => c.isError)
    ? "Errors present"
    : good.length < 2
      ? "Need ≥2 calls"
      : same
        ? "Matching results"
        : "Results differ";
  $("#result").textContent = pretty(call.result);
  $("#resultPanel").hidden = false;
}
async function run(count, invalid = false) {
  if (busy || !tools.length) return;
  busy = true;
  lock();
  reset();
  const name = invalid ? "getShipmentStatus" : $("#tool").value;
  const params = invalid ? { trackingId: "BAD" } : args();
  const generation = ++runGeneration;
  const wasZero =
    !paused && runtime?.readyReplicas === 0 && Date.now() - observedAt < 6000;
  try {
    for (let i = 0; i < count; i++) {
      status("#runStatus", `Executing ${name}: ${i + 1}/${count}…`);
      window.HeliosFlow.begin("tool");
      const started = performance.now();
      try {
        const call = await json(
          "agent/invoke/" + encodeURIComponent(name),
          params,
        );
        if (generation !== runGeneration) return;
        const elapsed = performance.now() - started;
        showCall(call, elapsed);
        if (wasZero && i === 0) {
          activationMs = elapsed;
          $("#activation").textContent =
            `First invocation after a recent zero-ready observation: ${Math.round(elapsed)} ms end-to-end. This includes network and activation, not just JVM startup.`;
        } else if (activationMs && i > 0) {
          $("#activation").textContent =
            `First after zero-ready: ${Math.round(activationMs)} ms · subsequent call: ${Math.round(elapsed)} ms end-to-end.`;
        }
      } catch (e) {
        window.HeliosFlow.fail();
        calls.push({ isError: true });
        const article = node("article", e.message, "call error");
        $("#calls").append(article);
        $("#consistency").textContent = "Errors present";
      }
    }
    status(
      "#runStatus",
      `Completed ${count} attempt${count === 1 ? "" : "s"}. Errors are retained; calls were sequential and used identical arguments.`,
    );
  } finally {
    busy = false;
    lock();
  }
}
$("#toolForm").addEventListener("submit", (e) => {
  e.preventDefault();
  run(1);
});
$("#repeat").addEventListener("click", () => run(5));
$("#invalid").addEventListener("click", () => run(1, true));
async function ask() {
  if (asking) return;
  const question = $("#question").value.trim();
  if (!question) return;
  asking = true;
  window.HeliosFlow.reset();
  window.HeliosFlow.begin("ask");
  $("#askButton").disabled = true;
  document
    .querySelectorAll("#examples button")
    .forEach((el) => (el.disabled = true));
  status("#askStatus", "Waiting for the supervisor and its executed tools…");
  $("#answer").hidden = true;
  $("#trace").hidden = true;
  try {
    const result = await json("agent/ask", { question }, 100000);
    $("#answerText").textContent = result.answer;
    $("#answer").hidden = false;
    $("#traceSteps").replaceChildren();
    for (const event of result.trace || []) {
      window.HeliosFlow.complete(event.call);
      const step = node("article", undefined, "call");
      step.append(node("h3", event.specialist + " · " + event.call.tool));
      step.append(
        node(
          "p",
          `Replica: ${event.call.evidence?.instanceId || "unknown"} · ${event.call.durationMs} ms · ${event.call.isError ? "Tool error" : "Success"}`,
        ),
      );
      const detail = node("details");
      detail.append(
        node("summary", "Arguments and business result"),
        node(
          "pre",
          pretty({
            arguments: event.call.evidence?.request?.params?.arguments,
            result: event.call.result,
          }),
        ),
      );
      step.append(detail);
      const inspect = node("button", "Inspect protocol");
      inspect.addEventListener("click", () => {
        protocol(event.call);
        tab("protocol");
      });
      step.append(inspect);
      $("#traceSteps").append(step);
      protocol(event.call);
    }
    $("#trace").hidden = false;
    if (!result.trace?.length)
      $("#traceSteps").append(
        node("p", "No MCP tool executions were captured for this answer."),
      );
    status("#askStatus", "Agent response received.");
  } catch (e) {
    window.HeliosFlow.fail();
    status("#askStatus", e.message, true);
  } finally {
    asking = false;
    window.HeliosFlow.idle();
    $("#askButton").disabled = false;
    document
      .querySelectorAll("#examples button")
      .forEach((el) => (el.disabled = false));
  }
}
$("#askForm").addEventListener("submit", (e) => {
  e.preventDefault();
  ask();
});
$("#question").addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    ask();
  }
});
for (const q of questions) {
  const b = node("button", q);
  b.type = "button";
  b.addEventListener("click", () => {
    if (asking) return;
    $("#question").value = q;
    ask();
  });
  $("#examples").append(b);
}
(async () => {
  await observe();
  if (runtime && !runtime.scaleToZero) await discover();
  else
    status(
      "#catalogStatus",
      "Discovery is explicit in serverless mode so opening this page does not activate the MCP service.",
    );
})();
