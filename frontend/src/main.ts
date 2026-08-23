import { CommonModule } from "@angular/common";
import { HttpClient, provideHttpClient } from "@angular/common/http";
import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from "@angular/core";
import { bootstrapApplication } from "@angular/platform-browser";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { forkJoin, timer } from "rxjs";
import { switchMap } from "rxjs/operators";

type DelayStatus = "EARLY" | "ON_TIME" | "LATE" | "UNKNOWN";

interface Route {
  agencyId: string;
  id: string;
  shortName: string;
  longName: string;
  color: string | null;
  textColor: string | null;
  tripCount: number;
}

interface Vehicle {
  agencyId: string;
  vehicleId: string;
  tripId: string | null;
  routeId: string | null;
  routeShortName: string | null;
  routeLongName: string | null;
  latitude: number;
  longitude: number;
  bearing: number | null;
  speed: number | null;
  currentStatus: string | null;
  delaySeconds: number | null;
  delayStatus: DelayStatus;
  nextStopId: string | null;
  recordedAt: string;
}

interface RouteStatus {
  routeId: string;
  shortName: string;
  longName: string;
  activeVehicles: number;
  earlyVehicles: number;
  onTimeVehicles: number;
  lateVehicles: number;
  unknownVehicles: number;
  averageDelaySeconds: number | null;
  lastUpdated: string | null;
}

@Component({
  selector: "app-root",
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="shell">
      <header class="topbar">
        <a class="brand" href="#" aria-label="RoutePulse home">
          <span class="brand-mark"><i></i><i></i><i></i></span>
          <span><b>RoutePulse</b><small>RALEIGH TRANSIT INTELLIGENCE</small></span>
        </a>
        <div class="top-actions">
          <span class="connection" [class.offline]="connectionState === 'offline'">
            <i></i>{{ connectionLabel }}
          </span>
          <button class="refresh" type="button" (click)="refresh()" [disabled]="loading">
            <span [class.spinning]="loading">↻</span> Refresh
          </button>
        </div>
      </header>

      <main>
        <section class="intro">
          <div>
            <p class="eyebrow">LIVE OPERATIONS · NC STATE WOLFLINE</p>
            <h1>Raleigh, in motion.</h1>
            <p class="intro-copy">A real-time view of active vehicles, route health, and schedule performance.</p>
          </div>
          <label class="route-picker">
            <span>ROUTE VIEW</span>
            <select [value]="selectedRouteId" (change)="selectRoute($any($event.target).value)">
              <option value="all">All Wolfline routes</option>
              <option *ngFor="let route of routes" [value]="route.id">
                {{ route.shortName }} · {{ route.longName }}
              </option>
            </select>
          </label>
        </section>

        <section class="metrics" aria-label="Live network summary">
          <article>
            <span class="metric-icon vehicles">↗</span>
            <div><small>ACTIVE VEHICLES</small><strong>{{ filteredVehicles.length }}</strong></div>
            <p><i></i> reporting now</p>
          </article>
          <article>
            <span class="metric-icon routes">⌁</span>
            <div><small>ROUTES REPORTING</small><strong>{{ reportingRoutes }}</strong></div>
            <p>of {{ routes.length }} scheduled routes</p>
          </article>
          <article>
            <span class="metric-icon performance">✓</span>
            <div><small>KNOWN PERFORMANCE</small><strong>{{ knownPerformance }}%</strong></div>
            <p>vehicles with delay data</p>
          </article>
          <article>
            <span class="metric-icon freshness">◷</span>
            <div><small>DATA FRESHNESS</small><strong>{{ freshnessLabel }}</strong></div>
            <p>15-second polling cycle</p>
          </article>
        </section>

        <section class="workspace">
          <article class="map-card">
            <header class="panel-head">
              <div><small>LIVE VEHICLE MAP</small><h2>{{ selectedRouteName }}</h2></div>
              <div class="legend"><span><i class="dot on-time"></i>On time</span><span><i class="dot late"></i>Late</span><span><i class="dot unknown"></i>Unknown</span></div>
            </header>
            <div class="map">
              <svg class="road-layer" viewBox="0 0 900 560" preserveAspectRatio="none" aria-hidden="true">
                <path class="road minor" d="M-20 420 C180 330 260 430 450 320 S700 150 940 230" />
                <path class="road major" d="M-30 150 C140 210 220 175 370 245 S650 400 930 350" />
                <path class="road major" d="M190 -20 C250 160 360 200 410 330 S520 500 590 590" />
                <path class="road minor" d="M670 -20 C600 150 570 210 620 330 S760 490 830 590" />
                <path class="road minor" d="M20 510 C180 490 270 520 390 450 S570 310 720 300" />
              </svg>
              <div class="district campus">NC STATE<br><span>MAIN CAMPUS</span></div>
              <div class="district centennial">CENTENNIAL<br><span>CAMPUS</span></div>
              <span class="map-label downtown">DOWNTOWN RALEIGH →</span>
              <span class="map-label western">WESTERN BLVD</span>

              <button
                *ngFor="let vehicle of filteredVehicles; trackBy: trackVehicle"
                [class]="'vehicle-marker ' + statusClass(vehicle.delayStatus)"
                [style.left.%]="markerPosition(vehicle).left"
                [style.top.%]="markerPosition(vehicle).top"
                [style.--route-color]="routeColor(vehicle.routeId)"
                [title]="vehicleTitle(vehicle)"
                type="button">
                <span>{{ vehicle.routeShortName || 'BUS' }}</span>
              </button>

              <div class="empty-map" *ngIf="!loading && filteredVehicles.length === 0">
                <span>◌</span><b>No active vehicles</b><small>Try “All Wolfline routes” or enable live ingestion.</small>
              </div>
              <div class="map-stamp"><i></i> LIVE · {{ lastUpdatedLabel }}</div>
            </div>
          </article>

          <aside class="side-panel">
            <header class="panel-head">
              <div><small>VEHICLE FEED</small><h2>{{ filteredVehicles.length }} active</h2></div>
              <span class="live-pill">LIVE</span>
            </header>
            <div class="vehicle-list" *ngIf="filteredVehicles.length; else noVehicles">
              <article *ngFor="let vehicle of filteredVehicles; trackBy: trackVehicle">
                <span class="route-badge" [style.background]="routeColor(vehicle.routeId)">{{ vehicle.routeShortName || '—' }}</span>
                <div class="vehicle-copy">
                  <b>{{ vehicle.routeLongName || 'Wolfline vehicle' }}</b>
                  <small>Bus {{ vehicle.vehicleId }} · {{ relativeTime(vehicle.recordedAt) }}</small>
                </div>
                <span [class]="'status ' + statusClass(vehicle.delayStatus)">{{ statusLabel(vehicle) }}</span>
              </article>
            </div>
            <ng-template #noVehicles><div class="empty-list">Waiting for live vehicle reports.</div></ng-template>

            <div class="route-health">
              <small>SELECTED ROUTE HEALTH</small>
              <ng-container *ngIf="selectedRouteId !== 'all' && selectedStatus; else networkPrompt">
                <div class="health-row"><span>On time</span><b>{{ selectedStatus.onTimeVehicles }}</b></div>
                <div class="health-row"><span>Early / late</span><b>{{ selectedStatus.earlyVehicles }} / {{ selectedStatus.lateVehicles }}</b></div>
                <div class="health-row"><span>Delay unknown</span><b>{{ selectedStatus.unknownVehicles }}</b></div>
                <div class="health-row"><span>Average delay</span><b>{{ formatDelay(selectedStatus.averageDelaySeconds) }}</b></div>
              </ng-container>
              <ng-template #networkPrompt><p>Select a route to inspect its operating status.</p></ng-template>
            </div>
          </aside>
        </section>

        <footer>
          <span>RoutePulse · Wolfline live operations</span>
          <span>GTFS schedule + GTFS-Realtime · Updated {{ lastUpdatedLabel }}</span>
        </footer>
      </main>
    </div>
  `,
})
class App implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  routes: Route[] = [];
  vehicles: Vehicle[] = [];
  selectedStatus: RouteStatus | null = null;
  selectedRouteId = "all";
  connectionState: "loading" | "live" | "offline" = "loading";
  loading = true;
  lastRefresh: Date | null = null;

  ngOnInit(): void {
    timer(0, 15_000)
      .pipe(
        switchMap(() => {
          this.loading = true;
          return forkJoin({
            routes: this.http.get<Route[]>("/api/v1/routes"),
            vehicles: this.http.get<Vehicle[]>("/api/v1/vehicles?activeWithinMinutes=5"),
          });
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ routes, vehicles }) => {
          this.applySnapshot(routes, vehicles);
        },
        error: () => {
          this.connectionState = "offline";
          this.loading = false;
          this.changeDetector.detectChanges();
        },
      });
  }

  refresh(): void {
    this.loading = true;
    forkJoin({
      routes: this.http.get<Route[]>("/api/v1/routes"),
      vehicles: this.http.get<Vehicle[]>("/api/v1/vehicles?activeWithinMinutes=5"),
    }).subscribe({
      next: ({ routes, vehicles }) => this.applySnapshot(routes, vehicles),
      error: () => {
        this.connectionState = "offline";
        this.loading = false;
        this.changeDetector.detectChanges();
      },
    });
  }

  private applySnapshot(routes: Route[], vehicles: Vehicle[]): void {
    this.routes = routes;
    this.vehicles = vehicles;
    this.lastRefresh = new Date();
    this.connectionState = "live";
    this.loading = false;
    this.loadRouteStatus();
    this.changeDetector.detectChanges();
  }

  selectRoute(routeId: string): void {
    this.selectedRouteId = routeId;
    this.loadRouteStatus();
  }

  private loadRouteStatus(): void {
    if (this.selectedRouteId === "all") {
      this.selectedStatus = null;
      return;
    }
    this.http.get<RouteStatus>(`/api/v1/routes/${this.selectedRouteId}/status?activeWithinMinutes=5`)
      .subscribe({
        next: status => {
          this.selectedStatus = status;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.selectedStatus = null;
          this.changeDetector.detectChanges();
        },
      });
  }

  get filteredVehicles(): Vehicle[] {
    return this.selectedRouteId === "all"
      ? this.vehicles
      : this.vehicles.filter(vehicle => vehicle.routeId === this.selectedRouteId);
  }

  get reportingRoutes(): number {
    return new Set(this.vehicles.map(vehicle => vehicle.routeId).filter(Boolean)).size;
  }

  get knownPerformance(): number {
    if (!this.filteredVehicles.length) return 0;
    const known = this.filteredVehicles.filter(vehicle => vehicle.delayStatus !== "UNKNOWN").length;
    return Math.round(known / this.filteredVehicles.length * 100);
  }

  get selectedRouteName(): string {
    if (this.selectedRouteId === "all") return "All active Wolfline vehicles";
    const route = this.routes.find(item => item.id === this.selectedRouteId);
    if (!route) return "Selected route";
    return route.longName.toLowerCase() === `route ${route.shortName}`.toLowerCase()
      ? route.longName
      : `Route ${route.shortName} · ${route.longName}`;
  }

  get connectionLabel(): string {
    return this.connectionState === "live" ? "Live data connected" : this.connectionState === "offline" ? "API unavailable" : "Connecting…";
  }

  get lastUpdatedLabel(): string {
    return this.lastRefresh ? this.lastRefresh.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" }) : "waiting";
  }

  get freshnessLabel(): string {
    if (!this.vehicles.length) return "—";
    const newest = Math.max(...this.vehicles.map(vehicle => new Date(vehicle.recordedAt).getTime()));
    const seconds = Math.max(0, Math.floor((Date.now() - newest) / 1000));
    return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m`;
  }

  markerPosition(vehicle: Vehicle): { left: number; top: number } {
    const left = (vehicle.longitude - -78.725) / (-78.635 - -78.725) * 100;
    const top = (35.825 - vehicle.latitude) / (35.825 - 35.755) * 100;
    return { left: Math.min(96, Math.max(4, left)), top: Math.min(94, Math.max(6, top)) };
  }

  routeColor(routeId: string | null): string {
    const color = this.routes.find(route => route.id === routeId)?.color;
    return color ? `#${color.replace("#", "")}` : "#9bea62";
  }

  statusClass(status: DelayStatus): string {
    return status.toLowerCase().replace("_", "-");
  }

  statusLabel(vehicle: Vehicle): string {
    if (vehicle.delaySeconds == null) return "No delay data";
    if (vehicle.delayStatus === "ON_TIME") return "On time";
    return this.formatDelay(vehicle.delaySeconds);
  }

  formatDelay(seconds: number | null): string {
    if (seconds == null) return "Unknown";
    if (Math.abs(seconds) < 60) return `${seconds > 0 ? "+" : ""}${seconds}s`;
    const minutes = Math.round(seconds / 60);
    return `${minutes > 0 ? "+" : ""}${minutes} min`;
  }

  relativeTime(timestamp: string): string {
    const seconds = Math.max(0, Math.floor((Date.now() - new Date(timestamp).getTime()) / 1000));
    return seconds < 60 ? `${seconds}s ago` : `${Math.floor(seconds / 60)}m ago`;
  }

  vehicleTitle(vehicle: Vehicle): string {
    return `${vehicle.routeLongName || "Wolfline"} · Bus ${vehicle.vehicleId} · ${this.statusLabel(vehicle)}`;
  }

  trackVehicle(_: number, vehicle: Vehicle): string {
    return vehicle.vehicleId;
  }
}

bootstrapApplication(App, { providers: [provideHttpClient()] }).catch(console.error);
