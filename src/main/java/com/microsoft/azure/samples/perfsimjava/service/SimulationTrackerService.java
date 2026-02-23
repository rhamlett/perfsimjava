package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.model.Simulation;
import com.microsoft.azure.samples.perfsimjava.model.SimulationStatus;
import com.microsoft.azure.samples.perfsimjava.model.SimulationType;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * =============================================================================
 * SIMULATION TRACKER SERVICE — Active Simulation Registry
 * =============================================================================
 *
 * PURPOSE:
 *   Maintains a central registry of all active simulations. Provides CRUD-like
 *   operations for simulation lifecycle management. Used by simulation services
 *   to register their work and by controllers to list/stop simulations.
 *
 * THREAD SAFETY:
 *   Uses ConcurrentHashMap for thread-safe access from multiple simulation
 *   threads and HTTP request handlers.
 *
 * PORTING NOTES:
 *   - Node.js: Map<string, Simulation> with simple get/set (single-threaded)
 *   - Python: dict with threading.Lock for thread safety
 *   - C#: ConcurrentDictionary<string, Simulation>
 *   - PHP: Session or shared storage (Redis) for multi-process PHP-FPM
 */
@Service
public class SimulationTrackerService {

    private final Map<String, Simulation> simulations = new ConcurrentHashMap<>();

    /**
     * Creates and registers a new simulation.
     *
     * @param type The type of simulation
     * @param parameters Simulation-specific parameters
     * @param durationSeconds Expected duration (for scheduling end time)
     * @return The created simulation record
     */
    public Simulation createSimulation(SimulationType type, Map<String, Object> parameters, int durationSeconds) {
        Simulation simulation = new Simulation(type, parameters, durationSeconds);
        simulations.put(simulation.getId(), simulation);
        return simulation;
    }

    /**
     * Gets a simulation by ID.
     *
     * @param id The simulation ID
     * @return The simulation, or null if not found
     */
    public Simulation getSimulation(String id) {
        return simulations.get(id);
    }

    /**
     * Gets all simulations.
     *
     * @return Collection of all simulations
     */
    public Collection<Simulation> getAllSimulations() {
        return simulations.values();
    }

    /**
     * Gets all active simulations.
     *
     * @return List of active simulations
     */
    public List<Simulation> getActiveSimulations() {
        return simulations.values().stream()
                .filter(s -> s.getStatus() == SimulationStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    /**
     * Gets all active simulations of a specific type.
     *
     * @param type The simulation type to filter by
     * @return List of active simulations of the specified type
     */
    public List<Simulation> getActiveSimulationsByType(SimulationType type) {
        return simulations.values().stream()
                .filter(s -> s.getStatus() == SimulationStatus.ACTIVE && s.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Marks a simulation as completed.
     *
     * @param id The simulation ID
     * @return The updated simulation, or null if not found
     */
    public Simulation completeSimulation(String id) {
        Simulation simulation = simulations.get(id);
        if (simulation != null) {
            simulation.complete();
        }
        return simulation;
    }

    /**
     * Marks a simulation as stopped (user-initiated).
     *
     * @param id The simulation ID
     * @return The updated simulation, or null if not found
     */
    public Simulation stopSimulation(String id) {
        Simulation simulation = simulations.get(id);
        if (simulation != null) {
            simulation.stop();
        }
        return simulation;
    }

    /**
     * Marks a simulation as failed.
     *
     * @param id The simulation ID
     * @return The updated simulation, or null if not found
     */
    public Simulation failSimulation(String id) {
        Simulation simulation = simulations.get(id);
        if (simulation != null) {
            simulation.fail();
        }
        return simulation;
    }

    /**
     * Removes a simulation from the registry.
     *
     * @param id The simulation ID
     * @return The removed simulation, or null if not found
     */
    public Simulation removeSimulation(String id) {
        return simulations.remove(id);
    }

    /**
     * Cleans up completed simulations older than the specified age.
     *
     * @param maxAgeSeconds Maximum age in seconds
     */
    public void cleanupOldSimulations(long maxAgeSeconds) {
        long cutoffTime = System.currentTimeMillis() - (maxAgeSeconds * 1000);
        simulations.entrySet().removeIf(entry -> {
            Simulation sim = entry.getValue();
            return sim.getStatus() != SimulationStatus.ACTIVE &&
                   sim.getStoppedAt() != null &&
                   sim.getStoppedAt().toEpochMilli() < cutoffTime;
        });
    }
}
