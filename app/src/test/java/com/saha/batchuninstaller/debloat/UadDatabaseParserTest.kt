package com.saha.batchuninstaller.debloat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UadDatabaseParserTest {
    @Test fun parsesRepresentativeUpstreamEntry() {
        val json = """{"com.example.browser":{"list":"Oem","description":"Browser\nCan be removed if replaced.","dependencies":[],"neededBy":[],"labels":[],"removal":"Recommended"}}"""
        val record = UadDatabaseParser.parse(json).getValue("com.example.browser")
        assertEquals("Oem", record.list)
        assertEquals("Recommended", record.removal)
        assertTrue(record.description!!.contains("Browser"))
        assertEquals(DebloatRisk.RECOMMENDED, UadRiskMapper.fromRemoval(record.removal))
    }

    @Test fun toleratesNullOptionalFields() {
        val record = UadDatabaseParser.parse("""{"com.example.service":{"list":"Aosp","description":"Notes","dependencies":null,"neededBy":null,"labels":null,"removal":"Advanced"}}""").getValue("com.example.service")
        assertTrue(record.dependencies!!.isEmpty())
        assertEquals(DebloatRisk.CAUTION, UadRiskMapper.fromRemoval(record.removal))
    }

    @Test fun mapsRiskConservatively() {
        assertEquals(DebloatRisk.HIGH_RISK, UadRiskMapper.fromRemoval("Unsafe"))
        assertEquals(DebloatRisk.HIGH_RISK, UadRiskMapper.fromRemoval("Expert"))
        assertEquals(DebloatRisk.UNKNOWN, UadRiskMapper.fromRemoval(null))
        assertEquals(DebloatRisk.UNKNOWN, UadRiskMapper.fromRemoval("FutureCategory"))
    }

    @Test fun rejectsMalformedOrEmptyData() {
        assertTrue(runCatching { UadDatabaseParser.parse("{") }.isFailure)
        assertTrue(runCatching { UadDatabaseParser.parse("{}") }.isFailure)
        assertTrue(runCatching { UadDatabaseParser.parse(" ") }.isFailure)
    }

    @Test fun ignoresInvalidPackageIdentifiers() {
        val records = UadDatabaseParser.parse("""{"not a package":{"list":"Misc","removal":"Recommended"},"com.valid.app":{"removal":"Future"}}""")
        assertFalse(records.containsKey("not a package"))
        assertEquals(DebloatRisk.UNKNOWN, UadRiskMapper.fromRemoval(records["com.valid.app"]?.removal))
        assertNull(records["com.missing.app"])
    }
}
