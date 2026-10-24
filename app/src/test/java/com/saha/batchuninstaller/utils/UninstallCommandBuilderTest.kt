package com.saha.batchuninstaller.utils

import com.saha.batchuninstaller.debloat.UninstallMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallCommandBuilderTest {
    @Test fun userAppCommandsNeverTargetSystemPartitions() {
        val restorable = UninstallCommandBuilder.build("com.example.user", false, UninstallMode.RESTORABLE)
        val permanent = UninstallCommandBuilder.build("com.example.user", false, UninstallMode.PERMANENT)
        assertNotNull(restorable)
        assertNotNull(permanent)
        assertTrue(restorable!!.contains("pm uninstall com.example.user"))
        assertFalse(restorable.contains("--user 0"))
        assertFalse(permanent!!.contains("pm clear"))
        assertFalse(permanent.contains("/system"))
    }

    @Test fun restorableSystemAppUsesUserScopedKeepDataCommand() {
        val command = UninstallCommandBuilder.build("com.vendor.service", true, UninstallMode.RESTORABLE)
        assertNotNull(command)
        assertTrue(command!!.contains("pm uninstall -k --user 0 com.vendor.service"))
        assertFalse(command.contains("rm "))
        assertFalse(command.contains("/system/"))
    }

    @Test fun permanentSystemAppClearsUserDataWithoutDeletingSystemApkFiles() {
        val command = UninstallCommandBuilder.build("com.vendor.service", true, UninstallMode.PERMANENT)
        assertNotNull(command)
        assertTrue(command!!.contains("pm uninstall --user 0 com.vendor.service"))
        assertTrue(command.contains("pm clear --user 0 com.vendor.service"))
        assertFalse(command.contains("rm "))
        assertFalse(command.contains("/system/"))
    }

    @Test fun rejectsShellInjectionAndInvalidPackageNames() {
        assertNull(UninstallCommandBuilder.build("com.example; reboot", false, UninstallMode.PERMANENT))
        assertNull(UninstallCommandBuilder.build("$(id)", true, UninstallMode.RESTORABLE))
        assertNull(UninstallCommandBuilder.build("", false, UninstallMode.RESTORABLE))
        assertNull(UninstallCommandBuilder.build("com.example..bad", false, UninstallMode.PERMANENT))
    }
}
