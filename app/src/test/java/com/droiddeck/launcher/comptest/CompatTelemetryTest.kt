package com.droiddeck.launcher.comptest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompatTelemetryTest {
    @Test fun millidegreeParses() {
        assertEquals(45.25, CompatTelemetry.parseMillidegreeC("45250")!!, 1e-9)
        assertEquals(36.0, CompatTelemetry.parseMillidegreeC("36000\n")!!, 1e-9)
    }

    @Test fun millidegreeRejectsGarbage() {
        assertNull(CompatTelemetry.parseMillidegreeC(null))
        assertNull(CompatTelemetry.parseMillidegreeC(""))
        assertNull(CompatTelemetry.parseMillidegreeC("N/A"))
    }

    @Test fun zonePickerFindsCpuAndGpu() {
        val zones = mapOf(
            "thermal_zone0" to "cpu-cluster0",
            "thermal_zone1" to "gpu",
            "thermal_zone2" to "battery",
        )
        val (cpu, gpu) = CompatTelemetry.pickZones(zones)
        assertEquals("thermal_zone0", cpu)
        assertEquals("thermal_zone1", gpu)
    }

    @Test fun zonePickerFindsMaliLabel() {
        val zones = mapOf("thermal_zone5" to "mali-gpu", "thermal_zone0" to "cpuss")
        val (cpu, gpu) = CompatTelemetry.pickZones(zones)
        assertEquals("thermal_zone5", gpu)
        assertEquals("thermal_zone0", cpu)
    }

    @Test fun zonePickerDoesNotReuseGpuZoneForCpu() {
        val (cpu, gpu) = CompatTelemetry.pickZones(mapOf("thermal_zone0" to "gpu"))
        assertEquals("thermal_zone0", gpu)
        assertEquals(null, cpu)
    }

    @Test fun zonePickerEmptyIsNulls() {
        assertEquals(null to null, CompatTelemetry.pickZones(emptyMap()))
    }

    @Test fun batteryDrainMath() {
        val a = TelemetrySnapshot(null, null, 80, null, 1L)
        val b = TelemetrySnapshot(null, null, 77, null, 2L)
        assertEquals(3, CompatTelemetry.batteryDrainPct(a, b))
        assertNull(CompatTelemetry.batteryDrainPct(a, null))
        assertNull(CompatTelemetry.batteryDrainPct(null, b))
    }
}
