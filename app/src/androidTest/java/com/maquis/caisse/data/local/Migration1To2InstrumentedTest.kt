package com.maquis.caisse.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maquis.caisse.data.local.migrations.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de migrations Room (1→2 produits, 2→3 ventes).
 * Crée des bases intermédiaires sans dépendre des JSON de schéma exportés.
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2InstrumentedTest {

    private val testDb = "migration-test-products"

    @Test
    fun migrate1To2_createsProductsTable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        openRawDatabase(context, testDb, version = 1) { /* vide Sprint 0 */ }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
            .build()

        try {
            db.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(3, cursor.getInt(0))
            }
            assertTableExists(db, "products")
            assertTableExists(db, "sales")
            assertTableExists(db, "sale_items")
        } finally {
            db.close()
            context.deleteDatabase(testDb)
        }
    }

    @Test
    fun migrate2To3_createsSalesTables() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDb)

        openRawDatabase(context, testDb, version = 2) { db ->
            Migrations.MIGRATION_1_2.migrate(db)
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .addMigrations(Migrations.MIGRATION_2_3)
            .build()

        try {
            assertTableExists(db, "sales")
            assertTableExists(db, "sale_items")
        } finally {
            db.close()
            context.deleteDatabase(testDb)
        }
    }

    private fun openRawDatabase(
        context: android.content.Context,
        name: String,
        version: Int,
        onCreate: (SupportSQLiteDatabase) -> Unit,
    ) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase.close()
    }

    private fun assertTableExists(db: AppDatabase, table: String) {
        db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(table, cursor.getString(0))
        }
    }
}
