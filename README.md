# Performance Problem Simulator - Java Blessed Image

A Java 21 Spring Boot application designed to simulate various performance problems for testing Azure App Service diagnostics and monitoring tools.

## Overview

This application allows you to deliberately trigger different types of performance issues to:
- Learn how Azure diagnostic tools (AppLens, Application Insights, Log Analytics) detect and present problems
- Test alerting and monitoring configurations
- Train support engineers on diagnosing common issues
- Validate auto-scaling and recovery mechanisms

## Features

### Simulations

| Simulation | Description | Dashboard Controls |
|------------|-------------|-------------------|
| 🔥 **CPU Stress** | Worker threads perform PBKDF2 cryptographic operations | Duration, Intensity (High/Moderate) |
| 📊 **Memory Pressure** | Allocates heap memory chunks that stack up | Size (MB), Release button |
| 🧵 **Thread Pool Starvation** | Blocks servlet threads to exhaust the pool | Blocked Threads, Duration |
| 🔌 **Connection Pool** | Simulates JDBC connection pool exhaustion | Pool Size, Query Duration, Concurrent Queries, Timeout |
| ❌ **Failed Requests** | Generates HTTP 5xx errors visible in AppLens and App Insights | Number of Requests |
| 💥 **Crash** | Triggers various crash scenarios | Type: Fail Fast, Stack Overflow, Exception, OOM |

### Real-time Dashboard

- WebSocket-based live metrics (STOMP over SockJS)
- System metrics updated every 250ms
- Request latency probe updated every 100ms (for AppLens visibility)
- Interactive charts for CPU, Memory, Threads, and Latency
- Event log with timestamped entries (UTC)

## Technology Stack

- **Runtime**: Java 21 (Blessed Image)
- **Framework**: Spring Boot 3.3
- **Build**: Maven
- **WebSocket**: STOMP over SockJS
- **Metrics**: JMX MBeans
- **Charts**: Chart.js

## Deployment

This application is designed to run on Azure App Service using the Java 21 Blessed Image on Linux.

### GitHub Actions CI/CD (Recommended)

1. Create an Azure App Service (Linux, Java 21)
2. Download the publish profile from Azure Portal
3. Add it as a GitHub secret named `AZURE_WEBAPP_PUBLISH_PROFILE`
4. Push to `main` branch - the workflow will build and deploy automatically

### Manual Deployment

```bash
# Build the application
mvn clean package -DskipTests

# Deploy to Azure
az webapp deploy \
    --resource-group rg-perfsimjava \
    --name perfsimjava \
    --src-path target/perfsimjava-1.0.0.jar \
    --type jar
```

## API Endpoints

All simulations are controlled through the web dashboard. The following endpoints are available for programmatic access:

### Health & Metrics
- `GET /health` - Application health status
- `GET /health/live` - Liveness probe (Kubernetes)
- `GET /health/ready` - Readiness probe (Kubernetes)
- `GET /api/metrics` - Current system metrics

### Load Testing (External Use)
The `/api/loadtest` endpoint is designed for use with Azure Load Testing, JMeter, or k6:

```
GET /api/loadtest?workIterations=200&bufferSizeKb=20000&baselineDelayMs=500
```

This endpoint simulates realistic workloads that degrade under high concurrency.

### Admin
- `GET /api/simulations` - List all active simulations
- `GET /api/admin/status` - Admin status overview
- `GET /api/admin/events` - Event log entries

## WebSocket Topics

Connect to `/ws` using SockJS and STOMP protocol:

| Topic | Update Frequency | Content |
|-------|------------------|---------|
| `/topic/metrics` | 250ms | System metrics |
| `/topic/probe` | 100ms | Probe latency results |
| `/topic/events` | On event | Event log entries |
| `/topic/simulations` | On change | Simulation lifecycle updates |

## Configuration

Key settings in `application.properties`:

```properties
# Metrics broadcast interval
perfsim.metrics-interval-ms=250

# Health probe interval (for AppLens)
perfsim.probe-interval-ms=100

# Default simulation duration
perfsim.default-duration-ms=30000

# Maximum simulation duration
perfsim.max-duration-ms=120000
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `PAGE_FOOTER` | Custom HTML footer text displayed at the bottom of the dashboard. Supports HTML links. |
| `HEALTH_PROBE_RATE` | Latency probe interval in milliseconds. Default: 200ms. Minimum: 100ms. |
| `IDLE_TIMEOUT_MINUTES` | Idle timeout in minutes. When app is idle, health probes stop to reduce traffic. Default: 20. Set to 0 to disable. |

### HEALTH_PROBE_RATE

The `HEALTH_PROBE_RATE` environment variable controls how frequently the server sends health probe requests to measure request latency. Lower values provide more granular latency data but increase CPU overhead.

- **Default:** 200ms (5 probes/sec)
- **Minimum:** 100ms (values below this are automatically clamped)
- **Chart updates:** The latency chart continues to update at 100ms regardless of probe rate, using interpolation

**Example:**

```bash
# Set via Azure CLI
az webapp config appsettings set \
    --resource-group rg-perfsimjava \
    --name perfsimjava \
    --settings HEALTH_PROBE_RATE=400
```

```bash
# Set locally for testing
export HEALTH_PROBE_RATE=400
```

### PAGE_FOOTER

The `PAGE_FOOTER` environment variable allows you to customize the footer credits displayed on the dashboard. This is useful for attributing tools, teams, or linking to relevant resources.

**Example:**

```bash
# Set via Azure CLI
az webapp config appsettings set \
    --resource-group rg-perfsimjava \
    --name perfsimjava \
    --settings PAGE_FOOTER='Created by <a href="https://speckit.org/" target="_blank">SpecKit</a> and <a href="https://github.com/copilot" target="_blank">Github Copilot</a>'
```

```bash
# Set locally for testing
export PAGE_FOOTER='Created by <a href="https://speckit.org/" target="_blank">SpecKit</a> and <a href="https://github.com/copilot" target="_blank">Github Copilot</a>'
```

The footer is retrieved via the `/api/health/footer` endpoint and rendered in the dashboard's footer section. If `PAGE_FOOTER` is not set, the footer credits section is hidden.

### IDLE_TIMEOUT_MINUTES

The `IDLE_TIMEOUT_MINUTES` environment variable controls how long the application can remain idle before health probes are paused. This reduces unnecessary traffic to AppLens and Application Insights when the app is not being actively used.

- **Default:** 20 minutes
- **Minimum:** 1 minute (set to 0 to disable idle timeout entirely)
- **Wake-up:** The app automatically wakes when a page is loaded or a load test request is received

**Behavior when idle:**
- Local health probes stop being sent
- Frontend probes (Azure AppLens visibility) stop being sent
- Event log shows "Application going idle, no health probes being sent. There will be gaps in diagnostics and logs."

**Behavior when waking:**
- Page load or load test activity resets the idle timer
- Event log shows "App waking up from idle state. There may be gaps in diagnostics and logs."
- Health probes resume immediately

**Example:**

```bash
# Set via Azure CLI
az webapp config appsettings set \
    --resource-group rg-perfsimjava \
    --name perfsimjava \
    --settings IDLE_TIMEOUT_MINUTES=30
```

```bash
# Set locally for testing
export IDLE_TIMEOUT_MINUTES=30
```

```bash
# Disable idle timeout entirely
export IDLE_TIMEOUT_MINUTES=0
```

## Deploy to Azure App Service

### Using Azure CLI

```bash
# Create resources
az group create --name rg-perfsimjava --location eastus
az appservice plan create --name asp-perfsimjava --resource-group rg-perfsimjava --sku B2 --is-linux
az webapp create --name perfsimjava --resource-group rg-perfsimjava --plan asp-perfsimjava --runtime "JAVA:21-java21"

# Deploy
az webapp deploy --resource-group rg-perfsimjava --name perfsimjava --src-path target/perfsimjava-1.0.0.jar --type jar

# Enable WebSockets
az webapp config set --resource-group rg-perfsimjava --name perfsimjava --web-sockets-enabled true
```

### Using Maven Plugin

```bash
mvn azure-webapp:deploy
```

See [azure-deployment.html](/azure-deployment.html) for detailed deployment instructions.

## Project Structure

```
perfsimjava/
├── src/main/java/com/microsoft/azure/samples/perfsimjava/
│   ├── PerfSimJavaApplication.java       # Main entry point
│   ├── config/
│   │   ├── AppConfig.java                # Application configuration
│   │   └── WebSocketConfig.java          # STOMP/WebSocket setup
│   ├── model/
│   │   ├── Simulation.java               # Simulation entity
│   │   ├── SimulationStatus.java         # Status enum
│   │   ├── SimulationType.java           # Type enum
│   │   ├── SystemMetrics.java            # Metrics DTOs
│   │   ├── EventLogEntry.java            # Event log entry
│   │   └── dto/                          # Request DTOs
│   ├── service/
│   │   ├── SimulationTrackerService.java # Simulation registry
│   │   ├── EventLogService.java          # Event logging
│   │   ├── MetricsService.java           # JMX metrics collection
│   │   ├── CpuStressService.java         # CPU stress logic
│   │   ├── MemoryPressureService.java    # Memory allocation
│   │   ├── ThreadStarvationService.java  # Thread blocking
│   │   ├── ConnectionPoolService.java    # Connection pool exhaustion
│   │   ├── FailedRequestsService.java    # HTTP 5xx error generation
│   │   ├── CrashService.java             # Crash triggers
│   │   └── ProbeService.java             # Health probing
│   └── controller/
│       ├── HealthController.java         # Health endpoints
│       ├── MetricsController.java        # Metrics API
│       ├── CpuController.java            # CPU simulation API
│       ├── MemoryController.java         # Memory simulation API
│       ├── ThreadStarvationController.java # Thread starvation API
│       ├── ConnectionPoolController.java # Connection pool API
│       ├── FailedRequestsController.java # Failed requests API
│       ├── CrashController.java          # Crash simulation API
│       └── AdminController.java          # Admin endpoints
├── src/main/resources/
│   ├── application.properties            # Spring configuration
│   └── static/
│       ├── index.html                    # Dashboard
│       ├── docs.html                     # API documentation
│       ├── azure-diagnostics.html        # Azure diagnostics guide
│       ├── azure-deployment.html         # Deployment guide
│       ├── css/styles.css                # Stylesheet
│       └── js/
│           ├── charts.js                 # Chart.js integration
│           ├── socket-client.js          # STOMP WebSocket client
│           └── dashboard.js              # UI logic
└── pom.xml                               # Maven build configuration
```

## Repository

https://github.com/rhamlett/perfsimjava

## Related Projects

This is the Java implementation of the Performance Problem Simulator. Other implementations:

- **Node.js**: PerfSimNode
- **.NET Core**: PerfProblemSimulator-NETCore
- **PHP**: perfsimphp

## License

MIT License

## Contributing

Contributions welcome! Please read the contributing guidelines before submitting PRs.

---

**⚠️ Warning**: This application is designed for testing and training purposes. Never deploy to production environments where simulated failures could impact real users.
