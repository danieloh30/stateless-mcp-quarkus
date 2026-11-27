/* Live browser actions and server-confirmed replies; never synthetic replica traffic. */
(() => {
  "use strict";
  const root = document.querySelector("#topology");
  const fleet = document.querySelector("#flowReplicas");
  const svg = document.querySelector("#flowCanvas");
  const ns = "http://www.w3.org/2000/svg";
  const seen = new Map();
  let runtime = null;
  let lastId = null;
  let activityTimer;
  let activityKind;
  const labels = {
    dev: ["Direct HTTP", "Single MCP server"],
    compose: ["nginx · L7", "Local MCP fleet"],
    openshift: ["L7 → Service", "OpenShift MCP fleet"],
    knative: ["Knative", "Serverless revision"],
  };
  function el(tag, text, cls) {
    const item = document.createElement(tag);
    if (text !== undefined) item.textContent = text;
    if (cls) item.className = cls;
    return item;
  }
  function render() {
    const mode = runtime?.environment || "unknown";
    const [routing, name] = labels[mode] || ["Routing", "MCP fleet"];
    document.querySelector("#flowRoutingLabel").textContent = routing;
    document.querySelector("#flowFleetLabel").textContent = name;
    const ready = runtime?.readyReplicas;
    document.querySelector("#flowCounts").textContent =
      `${Number.isInteger(ready) ? ready : "Unknown"} ready · ${seen.size} process IDs observed`;
    fleet.replaceChildren();
    if (ready === 0) {
      fleet.append(el("div", "0 ready", "replica idle"));
      if (lastId) {
        const previous = el(
          "div",
          lastId.replace(/^helios-/, ""),
          "replica historical last-hit",
        );
        previous.dataset.instance = lastId;
        previous.title = `Last recorded reply: ${lastId}. Historical evidence; platform reports zero ready endpoints.`;
        previous.append(el("small", "last recorded reply"));
        fleet.append(previous);
      }
    } else {
      const slots = Number.isInteger(ready)
        ? Math.min(ready, 10)
        : mode === "dev"
          ? 1
          : mode === "compose"
            ? 5
            : Math.max(Math.min(seen.size, 10), 1);
      const ids = [...seen.keys()].slice(-slots);
      for (const id of ids) {
        const item = el("div", id.replace(/^helios-/, ""), "replica observed");
        item.dataset.instance = id;
        item.title = `Observed serving a completed call: ${id}. This does not establish current pod readiness.`;
        if (id === lastId) item.classList.add("last-hit");
        fleet.append(item);
      }
      for (let i = ids.length; i < slots; i++) {
        const text = mode === "dev" ? "MCP server" : "awaiting call";
        fleet.append(el("div", text, "replica unobserved"));
      }
    }
    root.dataset.mode = mode;
    requestAnimationFrame(draw);
  }
  function point(selector) {
    const box =
      typeof selector === "string" ? root.querySelector(selector) : selector;
    const rect = box.getBoundingClientRect();
    const bounds = root.getBoundingClientRect();
    return {
      x: rect.x - bounds.x + rect.width / 2,
      y: rect.y - bounds.y + rect.height / 2,
    };
  }
  function path(points) {
    return points.map((p, i) => `${i ? "L" : "M"} ${p.x} ${p.y}`).join(" ");
  }
  function draw() {
    if (!root.isConnected) return;
    const bounds = root.getBoundingClientRect();
    svg.setAttribute("viewBox", `0 0 ${bounds.width} ${bounds.height}`);
    svg.querySelectorAll(".network-line").forEach((item) => item.remove());
    const ids = [
      "#flowBrowser",
      "#flowAgent",
      "#flowRouter",
      "#flowFleet",
      "#flowDb",
    ];
    for (let i = 0; i < ids.length - 1; i++) {
      const line = document.createElementNS(ns, "path");
      line.classList.add("network-line");
      line.setAttribute("d", path([point(ids[i]), point(ids[i + 1])]));
      svg.prepend(line);
    }
  }
  function packet(points, kind) {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const dot = document.createElementNS(ns, "circle");
    dot.setAttribute("r", "4");
    dot.setAttribute("class", `network-packet ${kind}`);
    const motion = document.createElementNS(ns, "animateMotion");
    motion.setAttribute("path", path(points));
    motion.setAttribute("dur", "1.1s");
    motion.setAttribute("fill", "freeze");
    dot.append(motion);
    svg.append(dot);
    motion.beginElement();
    setTimeout(() => dot.remove(), 1150);
  }
  function begin(kind) {
    activityKind = kind;
    root.classList.remove("flow-error");
    root.classList.add("flow-busy");
    document.querySelector("#flowStatus").textContent =
      kind === "ask"
        ? "Question sent · waiting for actual tool evidence"
        : kind === "discover"
          ? "Tool discovery requested"
          : "Business request sent · destination not yet known";
    const nodes = [point("#flowBrowser"), point("#flowAgent")];
    if (kind !== "ask") nodes.push(point("#flowRouter"), point("#flowFleet"));
    packet(nodes, "outbound");
  }
  function complete(call) {
    const evidence = call?.evidence || {};
    const id = evidence.instanceId;
    if (id) {
      seen.delete(id);
      seen.set(id, Date.now());
      lastId = id;
    }
    render();
    root.classList.remove("flow-busy");
    root.classList.toggle("flow-error", call?.isError === true);
    document.querySelector("#flowStatus").textContent = call?.isError
      ? "Tool error received · no replica inferred without evidence"
      : id
        ? `Reply from ${id} · ${call.durationMs} ms agent-to-fleet`
        : "Reply received · serving replica unknown";
    requestAnimationFrame(() => {
      const replica = [...fleet.children].find(
        (item) => item.dataset.instance === id,
      );
      const target = point(replica || "#flowFleet");
      if (id && !call.isError) packet([point("#flowDb"), target], "inbound");
      packet(
        [
          target,
          point("#flowRouter"),
          point("#flowAgent"),
          point("#flowBrowser"),
        ],
        call?.isError ? "failed" : "inbound",
      );
    });
    clearTimeout(activityTimer);
    activityTimer = setTimeout(() => root.classList.remove("flow-reply"), 1500);
    root.classList.add("flow-reply");
  }
  function fail() {
    root.classList.remove("flow-busy");
    root.classList.add("flow-error");
    document.querySelector("#flowStatus").textContent =
      "Request failed · no serving replica inferred";
    packet([point("#flowAgent"), point("#flowBrowser")], "failed");
  }
  function idle() {
    if (root.classList.contains("flow-busy")) {
      document.querySelector("#flowStatus").textContent =
        activityKind === "ask"
          ? "Question finished · no tool executions recorded"
          : "Tool discovery finished";
    }
    root.classList.remove("flow-busy");
  }
  function reset() {
    seen.clear();
    lastId = null;
    root.classList.remove("flow-busy", "flow-error", "flow-reply");
    svg.querySelectorAll(".network-packet").forEach((item) => item.remove());
    document.querySelector("#flowStatus").textContent = "Waiting for a request";
    render();
  }
  new ResizeObserver(draw).observe(root);
  window.HeliosFlow = {
    runtime(value) {
      runtime = value;
      render();
    },
    begin,
    complete,
    fail,
    idle,
    reset,
  };
  render();
})();
