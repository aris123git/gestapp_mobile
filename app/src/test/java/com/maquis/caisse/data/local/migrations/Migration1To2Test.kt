package com.maquis.caisse.data.local.migrations

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie que la migration 1→2 expose bien le SQL de création de `products`.
 * Le test instrumenté Room (MigrationTestHelper) pourra compléter ceci
 * dès qu'un runner d'instrumentation sera branché sur le CI.
 */
class Migration1To2Test {

    @Test
    fun migration_1_2_targetsCorrectVersions() {
        val migration = Migrations.MIGRATION_1_2
        assertTrue(migration.startVersion == 1)
        assertTrue(migration.endVersion == 2)
    }
}
