package com.droiddeck.launcher.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSupportTest {
    @Test fun maliProbeCoversKbaseNodeSysfsAndVendorDriver() {
        assertTrue(DeviceSupport.maliProbePaths.contains("/dev/mali0"))
        assertTrue(DeviceSupport.maliProbePaths.contains("/sys/class/misc/mali0"))
        assertTrue(DeviceSupport.maliProbePaths.any { it.endsWith("vulkan.mali.so") })
    }

    @Test fun maliIsFalseOnNonMaliHost() {
        // This x86_64 host has no kbase node and no vendor Mali driver.
        assertFalse(DeviceSupport.mali())
    }
}
