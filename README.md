# Performance Problem Simulator - Java Blessed Image

A Java 25 Spring Boot application designed to simulate various performance problems for testing Azure App Service diagnostics and monitoring tools.

## Overview

This application allows you to deliberately trigger different types of performance issues to:
- Learn how Azure diagnostic tools (AppLens, Application Insights, Log Analytics) detect and present problems
- Test alerting and monitoring configurations
- Train support engineers on diagnosing common issues
- Validate auto-scaling and recovery mechanisms

## Features

### Simulations

| Simulation | Description | Use Case |
|------------|-------------|----------|
| 🔥 **CPU Stress** | Spawns worker threads performing PBKDF2 cryptographic operations | Test CPU throttling detection, auto-scaling triggers |
| 💾 **Memory Pressure** | Allocates heap memory chunks | Test memory alerts, GC behavior, OOM scenarios |
| ⏳ **Thread Pool Starvation** | Blocks servlet threads to exhaust the pool | Test request queuing, latency spikes (Java equivalent of Event Loop Blocking) |
| 🐢 **Slow Request** | Introduces artificial delays in responses | Test timeout detection, P95/P99 latency monitoring |
| 💥 **Crash** | Triggers various crash scenarios | Test instance restart detection, availability monitoring |

### Real-time Dashboard

- WebSocket-based live metrics (STOMP over SockJS)
- System metrics updated every 250ms
- Request latency probe updated every 100ms (for AppLens visibility)
- Interactive charts for CPU, Memory, Threads, and Latency
- Event log with color-coded entries

## Technology Stack

- **Runtime**: Java 25 (Blessed Image)
- **Framework**: Spring Boot 3.5
- **Build**: Maven
- **WebSocket**: STOMP over SockJS
- **Metrics**: JMX MBeans
- **Charts**: Chart.js

## Deployment

This application is designed to run on Azure App Service using the Java 25 Blessed Image on Linux.

### GitHub Actions CI/CD (Recommended)

1. Create an Azure App Service (Linux, Java 25)
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

### Health
- `GET /health` - Application health status
- `GET /health/live` - Liveness probe
- `GET /health/ready` - Readiness probe

### Metrics
- `GET /api/metrics` - Current system metrics

### CPU Stress
- `POST /api/simulations/cpu/stress` - Start CPU stress
- `DELETE /api/simulations/cpu/stress/{id}` - Stop simulation
- `GET /api/simulations/cpu/active` - List active simulations

### Memory Pressure
- `POST /api/simulations/memory/pressure` - Start memory pressure
- `DELETE /api/simulations/memory/pressure/{id}` - Release memory

### Thread Pool Starvation
- `POST /api/simulations/thread/starvation` - Start blocking threads

### Slow Request
- `GET /api/simulations/slow/request` - Trigger slow response

### Crash
- `POST /api/simulations/crash` - Trigger crash by type
- `POST /api/simulations/crash/failfast` - System.exit()
- `POST /api/simulations/crash/stackoverflow` - StackOverflowError
- `POST /api/simulations/crash/exception` - RuntimeException
- `POST /api/simulations/crash/oom` - OutOfMemoryError

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

## Deploy to Azure App Service

### Using Azure CLI

```bash
# Create resources
az group create --name rg-perfsimjava --location eastus
az appservice plan create --name asp-perfsimjava --resource-group rg-perfsimjava --sku B2 --is-linux
az webapp create --name perfsimjava --resource-group rg-perfsimjava --plan asp-perfsimjava --runtime "JAVA:25-java25"

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
│   │   ├── SlowRequestService.java       # Slow responses
│   │   ├── CrashService.java             # Crash triggers
│   │   └── ProbeService.java             # Health probing
│   └── controller/
│       ├── HealthController.java         # Health endpoints
│       ├── MetricsController.java        # Metrics API
│       ├── CpuController.java            # CPU simulation API
│       ├── MemoryController.java         # Memory simulation API
│       ├── ThreadStarvationController.java # Thread starvation API
│       ├── SlowRequestController.java    # Slow request API
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
