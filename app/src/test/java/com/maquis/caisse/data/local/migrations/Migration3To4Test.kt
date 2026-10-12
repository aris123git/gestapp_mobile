package com.maquis.caisse.data.local.migrations

import org.junit.Assert.assertEquals
import org.junit.Test

class Migration3To4Test {

    @Test
    fun migration_3_4_targetsCorrectVersions() {
        val migration = Migrations.MIGRATION_3_4
        assertEquals(3, migration.startVersion)
        assertEquals(4, migration.endVersion)
    }
}
