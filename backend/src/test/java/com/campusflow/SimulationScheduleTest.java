package com.campusflow;

import com.campusflow.simulation.*;
import com.campusflow.system.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.mockito.Mockito.*;

class SimulationScheduleTest {
    @Test void changingConfiguredPeriodChangesWhenNextSampleRuns() {
        var clock=mock(Clock.class);var configs=mock(ConfigService.class);var simulation=mock(SimulationService.class);
        var start=Instant.parse("2026-10-09T02:00:00Z");when(clock.instant()).thenReturn(start);
        when(configs.current()).thenReturn(new RuntimeConfig(1,5,30,60,.3,.3,.25,.15));
        var scheduler=new SimulationScheduler(simulation,configs,clock);
        when(clock.instant()).thenReturn(start.plusSeconds(3));scheduler.tick();verifyNoInteractions(simulation);
        when(configs.current()).thenReturn(new RuntimeConfig(2,2,30,60,.3,.3,.25,.15));
        scheduler.tick();verify(simulation,times(1)).tick();
        when(clock.instant()).thenReturn(start.plusSeconds(4));scheduler.tick();verify(simulation,times(1)).tick();
        when(clock.instant()).thenReturn(start.plusSeconds(5));scheduler.tick();verify(simulation,times(2)).tick();
    }
}
