import { bootstrapApplication } from "@angular/platform-browser";
import { Component } from "@angular/core";
@Component({
  selector: "app-root",
  standalone: true,
  template: `<main>
    <nav>
      <span class="mark">RP</span><b>RoutePulse</b><small>RALEIGH</small>
    </nav>
    <section class="hero">
      <p class="eyebrow">TRANSIT RELIABILITY INTELLIGENCE</p>
      <h1>See how Raleigh<br /><em>really moves.</em></h1>
      <p class="lede">
        Live operational insight for NC State Wolfline, built to reveal delays,
        reliability, and service patterns—not merely dots on a map.
      </p>
      <div class="metrics">
        <article>
          <strong>WOLFLINE</strong><span>First connected agency</span>
        </article>
        <article>
          <strong>15 SEC</strong><span>Live polling interval</span>
        </article>
        <article>
          <strong>3 TOPICS</strong><span>Streaming event foundation</span>
        </article>
      </div>
    </section>
    <footer>
      <span class="pulse"></span> Platform ready · Live ingestion opt-in
    </footer>
  </main>`,
})
class App {}
bootstrapApplication(App).catch(console.error);
