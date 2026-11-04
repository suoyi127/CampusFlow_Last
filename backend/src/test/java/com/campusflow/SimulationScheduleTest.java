package com.campusflow;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationScheduleTest {
    @Test void productionContainsNoSimulatorOrDemoInitializer() {
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.campusflow.simulation.SimulationService"));
        assertThrows(ClassNotFoundException.class,()->Class.forName("com.campusflow.simulation.SimulationScheduler"));
    }
}
