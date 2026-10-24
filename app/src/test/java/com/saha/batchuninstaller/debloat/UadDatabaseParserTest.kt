package com.saha.batchuninstaller.debloat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UadDatabaseParserTest {
    @Test fun parsesRealSourceArrayAndMapsFields() {
        val json = """[
            {
                "id": "com.example.browser",
                "label": "Example Browser",
                "description": "Can be removed after choosing another browser.",
                "dependencies": ["com.example.runtime"],
                "required_by": ["com.example.home"],
                "tags": ["browser"],
                "web": ["https://example.org"],
                "removal": "replace",
                "warning": "Check WebView compatibility first.",
                "suggestions": "browsers"
            }
        ]"""
        val record = UadDatabaseParser.parseCategory(json, "Google").getValue("com.example.browser")
        assertEquals("Google", record.list)
        assertEquals("Example Browser", record.label)
        assertEquals("replace", record.removal)
        assertEquals("Can be removed after choosing another browser.", record.description)
        assertEquals(listOf("com.example.runtime"), record.dependencies)
        assertEquals(listOf("com.example.home"), record.neededBy)
        assertEquals(listOf("browser"), record.labels)
        assertEquals(listOf("https://example.org"), record.web)
        assertEquals("browsers", record.suggestions)
        assertEquals("Check WebView compatibility first.", record.warning)
        assertEquals(DebloatRisk.REPLACE, UadRiskMapper.fromRemoval(record.removal))
    }

    @Test fun toleratesNullOptionalFieldsAndUnknownRemovalDefaultsToCaution() {
        val json = """[
            {"id":"com.example.service","removal":"future","dependencies":null,"required_by":null,"tags":null}
        ]"""
        val record = UadDatabaseParser.parseCategory(json, "AOSP").getValue("com.example.service")
        assertTrue(record.dependencies!!.isEmpty())
        assertTrue(record.neededBy!!.isEmpty())
        assertTrue(record.labels!!.isEmpty())
        assertEquals("caution", record.removal)
        assertEquals(DebloatRisk.CAUTION, UadRiskMapper.fromRemoval(record.removal))
    }

    @Test fun mapsAllRemovalValuesConservatively() {
        assertEquals(DebloatRisk.RECOMMENDED, UadRiskMapper.fromRemoval("delete"))
        assertEquals(DebloatRisk.REPLACE, UadRiskMapper.fromRemoval("replace"))
        assertEquals(DebloatRisk.CAUTION, UadRiskMapper.fromRemoval("caution"))
        assertEquals(DebloatRisk.HIGH_RISK, UadRiskMapper.fromRemoval("unsafe"))
        assertEquals(DebloatRisk.RECOMMENDED, UadRiskMapper.fromRemoval("Recommended"))
        assertEquals(DebloatRisk.UNKNOWN, UadRiskMapper.fromRemoval(null))
    }

    @Test fun rejectsMalformedOrEmptyData() {
        assertTrue(runCatching { UadDatabaseParser.parseCategory("{", "AOSP") }.isFailure)
        assertTrue(runCatching { UadDatabaseParser.parseCategory("[]", "AOSP") }.isFailure)
        assertTrue(runCatching { UadDatabaseParser.parseCategory(" ", "AOSP") }.isFailure)
        assertTrue(runCatching { UadDatabaseParser.parseCategory("{}", "AOSP") }.isFailure)
    }

    @Test fun discardsInvalidEntriesAndCountsThem() {
        val result = UadDatabaseParser.parseCategoryDetailed(
            """[
                {"id":"com.valid.app","removal":"delete"},
                {"id":"not a package","removal":"delete"},
                {"id":"com.missing.removal"},
                17
            ]""",
            "Misc"
        )
        assertTrue(result.entries.containsKey("com.valid.app"))
        assertFalse(result.entries.containsKey("not a package"))
        assertEquals(3, result.discardedEntryCount)
    }

    @Test fun mostRestrictiveDuplicateWins() {
        val aosp = mapOf("com.example.shared" to UadPackageRecord(list = "AOSP", removal = "delete"))
        val oem = mapOf("com.example.shared" to UadPackageRecord(list = "OEM", removal = "unsafe"))
        val result = UadDatabaseParser.mergeCategories(listOf(aosp, oem))
        assertEquals("unsafe", result.getValue("com.example.shared").removal)
        assertEquals("OEM", result.getValue("com.example.shared").list)
    }

    @Test fun supportsLegacyCacheDuringMigration() {
        val legacy = """{"com.example.old":{"list":"Oem","description":"Old cache","dependencies":[],"neededBy":[],"labels":[],"removal":"Recommended"}}"""
        val record = UadDatabaseParser.parse(legacy).getValue("com.example.old")
        assertEquals("Oem", record.list)
        assertEquals(DebloatRisk.RECOMMENDED, UadRiskMapper.fromRemoval(record.removal))
        assertNull(UadDatabaseParser.parse(legacy)["com.missing.app"])
    }
}
