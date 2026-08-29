---
hide:
  - navigation
  - toc
---

<section class="hero">
  <div class="hero__copy">
    <p class="hero__eyebrow">Quarkus · Java 25 · Model Context Protocol</p>
    <h1>Agent intelligence.<br><span>Stateless infrastructure.</span></h1>
    <p class="hero__lead">Build an OpenAI-powered supervisor that routes natural-language requests to specialist agents—and scales a fleet of MCP tool servers from zero to many without session affinity.</p>
    <div class="hero__actions">
      <a class="md-button md-button--primary" href="getting-started/">Run Helios locally</a>
      <a class="md-button" href="architecture/">Explore the architecture</a>
    </div>
  </div>
  <div class="hero__visual" aria-label="Request flow from user to agent and MCP fleet">
    <div class="flow-node flow-node--user"><small>ASK</small><strong>Where is HLX-10032291?</strong></div>
    <span class="flow-arrow">↓</span>
    <div class="flow-node flow-node--agent"><small>SUPERVISOR</small><strong>Routes to Shipment Agent</strong></div>
    <span class="flow-arrow">↓</span>
    <div class="flow-fleet">
      <span>MCP 01</span><span>MCP 02</span><span>MCP 03</span><span>MCP 04</span><span>MCP 05</span>
    </div>
    <p>Any request · Any replica · Same result</p>
  </div>
</section>

<div class="stat-strip">
  <div class="stat"><strong>5</strong><span>domain tools</span></div>
  <div class="stat"><strong>3</strong><span>specialist agents</span></div>
  <div class="stat"><strong>0</strong><span>server sessions</span></div>
  <div class="stat"><strong>0→N</strong><span>Knative replicas</span></div>
</div>

## Choose your path

<div class="path-grid">
  <a class="path-card" href="getting-started/">
    <span class="path-card__icon">01</span>
    <strong>Run the experience</strong>
    <span>Start the two services in dev mode or launch the complete five-replica topology.</span>
    <em>Get started →</em>
  </a>
  <a class="path-card" href="architecture/">
    <span class="path-card__icon">02</span>
    <strong>Understand the design</strong>
    <span>See how the supervisor, scoped toolboxes, load balancer, MCP fleet, and shared data fit together.</span>
    <em>View architecture →</em>
  </a>
  <a class="path-card" href="deployment/">
    <span class="path-card__icon">03</span>
    <strong>Scale on OpenShift</strong>
    <span>Deploy the platform, prove horizontal scaling, then switch the MCP tier to Knative scale-to-zero.</span>
    <em>Deploy Helios →</em>
  </a>
</div>

## One platform, two clean responsibilities

<div class="split-grid">
  <div class="feature-panel feature-panel--agent">
    <p class="panel-label">Agent tier</p>
    <h3>The front door and the brains</h3>
    <p>The browser console reaches an OpenAI-powered supervisor. It delegates each question to a shipment, inventory, or exception specialist with only the tools that specialist needs.</p>
    <ul><li>Natural-language routing</li><li>Scoped <code>@ToolBox</code> bridges</li><li>Managed MCP clients</li></ul>
  </div>
  <div class="feature-panel feature-panel--fleet">
    <p class="panel-label">MCP tier</p>
    <h3>Disposable compute, durable data</h3>
    <p>Identical Quarkus replicas expose validated business tools. They hold no session state, so a load balancer can send every call to a different instance.</p>
    <ul><li>Streamable HTTP</li><li>Jakarta validation</li><li>Shared PostgreSQL state</li></ul>
  </div>
</div>

## A demo you can see, not just describe

<figure class="showcase">
  <img src="images/helios-control-tower.png" alt="Helios Control Tower console showing the stateless MCP fleet">
  <figcaption><strong>Helios Control Tower</strong><span>Ask an agent, invoke raw tools, and watch successive requests rotate across ready replicas.</span></figcaption>
</figure>

<div class="cta-band">
  <div><p class="panel-label">Ready to fly?</p><h2>Launch the control tower.</h2></div>
  <a class="md-button md-button--primary" href="getting-started/">Start with dev mode</a>
</div>
