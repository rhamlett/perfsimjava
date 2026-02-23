package com.microsoft.azure.samples.perfsimjava.model;

/**
 * Lifecycle states for a simulation instance.
 *
 * State machine:
 *   ACTIVE → COMPLETED (duration elapsed)
 *   ACTIVE → STOPPED (user-initiated stop)
 *   ACTIVE → FAILED (error during simulation)
 *
 * Only ACTIVE simulations consume resources. Terminal states are immutable.
 */
public enum SimulationStatus {
    ACTIVE,
    COMPLETED,
    STOPPED,
    FAILED
}
