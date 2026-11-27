const { chromium } = require("playwright");
const assert = require("node:assert/strict");
const http = require("node:http");
const os = require("node:os");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(
  __dirname,
  "../agent/src/main/resources/META-INF/resources",
);
(async () => {
  const server = http.createServer((req, res) => {
    const name = req.url === "/" ? "index.html" : req.url.slice(1);
    if (
      ![
        "index.html",
        "console.js",
        "console.css",
        "topology.js",
        "topology.css",
      ].includes(name)
    ) {
      res.writeHead(404);
      return res.end();
    }
    res.setHeader(
      "Content-Type",
      name.endsWith(".js")
        ? "text/javascript"
        : name.endsWith(".css")
          ? "text/css"
          : "text/html",
    );
    res.end(fs.readFileSync(path.join(root, name)));
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const chrome = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
  const browser = await chromium.launch({
    headless: true,
    ...(chrome ? { executablePath: chrome } : {}),
  });
  try {
    for (const mode of ["dev", "compose", "openshift", "knative"]) {
      const page = await browser.newPage({
        viewport: { width: 1440, height: 1000 },
      });
      let hits = [],
        askCount = 0;
      const browserErrors = [];
      page.on("pageerror", (e) => browserErrors.push(e.message));
      await page.route("**/agent/**", async (route) => {
        const req = route.request(),
          endpoint = new URL(req.url()).pathname;
        hits.push(endpoint);
        let data;
        if (endpoint === "/agent/runtime")
          data = {
            environment: mode,
            burstEnabled: mode !== "dev",
            scaleToZero: mode === "knative",
            readyReplicas: mode === "dev" ? null : mode === "knative" ? 0 : 5,
            readinessSource: "test platform observer",
          };
        else if (endpoint === "/agent/catalog")
          data = [
            {
              name: "getShipmentStatus",
              description: '<img src=x onerror="window.injected=true">',
              params: ["trackingId"],
            },
          ];
        else if (endpoint.startsWith("/agent/invoke/")) {
          const args = req.postDataJSON();
          assert.deepEqual(args, { trackingId: "HLX-10032291" });
          data = {
            tool: "getShipmentStatus",
            result: { status: "IN_TRANSIT" },
            isError: false,
            durationMs: 5,
            evidence: {
              instanceId:
                "replica-" +
                hits.filter((x) => x.startsWith("/agent/invoke/")).length,
              transientConnection: true,
              requestHeaders: { "MCP-Protocol-Version": "2026-07-28" },
              request: { params: { arguments: args } },
            },
          };
        } else if (endpoint === "/agent/ask") {
          askCount++;
          await new Promise((r) => setTimeout(r, 120));
          data = {
            answer: '<img src=x onerror="window.injected=true">',
            trace: [
              {
                specialist: "shipmentAgent",
                call: {
                  tool: "getShipmentStatus",
                  result: { status: "IN_TRANSIT" },
                  isError: false,
                  durationMs: 7,
                  evidence: { instanceId: "replica-ai" },
                },
              },
            ],
          };
        } else throw Error("Unexpected endpoint " + endpoint);
        await route.fulfill({ json: data });
      });
      await page.goto(`http://127.0.0.1:${server.address().port}/`);
      await page.waitForFunction(() =>
        document.querySelector("#environment").textContent.includes("·"),
      );
      await page.locator('[data-tab="fleet"]').click();
      if (mode === "knative") {
        assert(!hits.includes("/agent/catalog"));
        assert.equal(await page.locator("#readyCount").textContent(), "0");
        await page
          .getByRole("button", {
            name: "Discover tools (may activate fleet)",
            exact: true,
          })
          .click();
      }
      await page.waitForFunction(
        () => document.querySelector("#tool").options.length === 1,
      );
      await page.locator('[data-tab="fleet"]').click();
      assert.equal(await page.locator("#repeat").isDisabled(), mode === "dev");
      await page.locator("#run").click();
      await page.waitForFunction(() =>
        document
          .querySelector("#runStatus")
          .textContent.startsWith("Completed"),
      );
      assert.equal(await page.locator("#observedCount").textContent(), "1");
      assert.equal(
        await page.locator("#topology .replica.observed").count(),
        mode === "knative" ? 0 : 1,
      );
      if (mode === "knative") {
        assert.equal(await page.locator(".replica.historical").count(), 1);
        assert.match(
          await page.locator(".replica.historical").textContent(),
          /last recorded reply/,
        );
      }
      assert.match(
        await page.locator("#flowStatus").textContent(),
        /Reply from replica-/,
      );
      if (mode !== "dev") {
        await page.locator("#repeat").click();
        await page.waitForFunction(() =>
          document
            .querySelector("#runStatus")
            .textContent.startsWith("Completed 5"),
        );
        assert.equal(await page.locator("#observedCount").textContent(), "5");
        assert.equal(
          await page.locator("#consistency").textContent(),
          "Matching results",
        );
      }
      await page.screenshot({
        path: path.join(
          process.env.HELIOS_SCREENSHOT_DIR || os.tmpdir(),
          `helios-${mode}-flow.png`,
        ),
      });
      assert(!hits.some((x) => x.includes("/instance")));
      assert.equal(await page.evaluate(() => window.injected), undefined);
      await page.locator('[data-tab="ask"]').click();
      await page.locator("#question").fill("Where is shipment HLX-10032291?");
      await page.locator("#question").press("Enter");
      await page.locator("#question").press("Enter");
      await page.waitForFunction(
        () =>
          document.querySelector("#askStatus").textContent ===
          "Agent response received.",
      );
      assert.equal(askCount, 1);
      assert.match(
        await page.locator("#flowStatus").textContent(),
        /Reply from replica-ai/,
      );
      await page.setViewportSize({ width: 390, height: 844 });
      await page.emulateMedia({ reducedMotion: "reduce" });
      await page.evaluate(() => window.HeliosFlow.begin("tool"));
      await page.waitForTimeout(1200);
      assert.equal(await page.locator(".network-packet").count(), 0);
      assert(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      );
      await page.evaluate(() => window.HeliosFlow.fail());
      assert.match(
        await page.locator("#flowStatus").textContent(),
        /Request failed/,
      );
      await page.screenshot({
        path: path.join(
          process.env.HELIOS_SCREENSHOT_DIR || os.tmpdir(),
          `helios-${mode}-mobile.png`,
        ),
      });
      await page.setViewportSize({ width: 1440, height: 1000 });
      assert.equal(await page.evaluate(() => window.injected), undefined);
      await page.locator("#pause").click();
      const count = hits.filter((x) => x === "/agent/runtime").length;
      await page.waitForTimeout(3200);
      assert.equal(hits.filter((x) => x === "/agent/runtime").length, count);
      await page.locator('[data-tab="fleet"]').click();
      await page.locator("#presentation").click();
      await page.screenshot({
        path: path.join(
          process.env.HELIOS_SCREENSHOT_DIR || os.tmpdir(),
          `helios-${mode}-spa.png`,
        ),
        fullPage: true,
      });
      assert.deepEqual(browserErrors, []);
      await page.close();
      console.log(
        "PASS",
        mode,
        "business calls, mode gates, safe rendering, duplicate guard, passive pause",
      );
    }
  } finally {
    await browser.close();
    await new Promise((r) => server.close(r));
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
