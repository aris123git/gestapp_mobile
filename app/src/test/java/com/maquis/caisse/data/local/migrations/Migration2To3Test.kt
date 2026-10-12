package com.maquis.caisse.data.local.migrations

import org.junit.Assert.assertEquals
import org.junit.Test

class Migration2To3Test {

    @Test
    fun migration_2_3_targetsCorrectVersions() {
        val migration = Migrations.MIGRATION_2_3
        assertEquals(2, migration.startVersion)
        assertEquals(3, migration.endVersion)
    }
}
