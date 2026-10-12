package com.maquis.caisse.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations Room explicites — jamais de fallback destructif.
 *
 * Sprint 0 livrait la base vide en version 1.
 * Sprint 1 crée la table `products` (version 2).
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `products` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `sale_price` INTEGER NOT NULL,
                    `purchase_price` INTEGER NOT NULL,
                    `stock` INTEGER NOT NULL,
                    `alert_threshold` INTEGER NOT NULL,
                    `image_path` TEXT,
                    `is_active` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    /** Sprint 2 : tables `sales` + `sale_items`. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sales` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `total_amount` INTEGER NOT NULL,
                    `payment_mode` TEXT NOT NULL,
                    `cash_amount` INTEGER NOT NULL,
                    `mobile_money_amount` INTEGER NOT NULL,
                    `voucher_amount` INTEGER NOT NULL,
                    `debt_amount` INTEGER NOT NULL,
                    `amount_tendered` INTEGER NOT NULL,
                    `change_amount` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sale_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sale_id` INTEGER NOT NULL,
                    `product_id` INTEGER NOT NULL,
                    `product_name` TEXT NOT NULL,
                    `unit_price` INTEGER NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `line_total` INTEGER NOT NULL,
                    FOREIGN KEY(`sale_id`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sale_items_sale_id` ON `sale_items` (`sale_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sale_items_product_id` ON `sale_items` (`product_id`)",
            )
        }
    }

    /** Dettes, avoirs et colonnes financières de session. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Nouvelles colonnes sur caisse_sessions
            db.execSQL("ALTER TABLE caisse_sessions ADD COLUMN `opening_balance` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE caisse_sessions ADD COLUMN `cash_sales` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE caisse_sessions ADD COLUMN `mobile_sales` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE caisse_sessions ADD COLUMN `debt_sales` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE caisse_sessions ADD COLUMN `cash_counted` INTEGER")

            // Table dettes
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dettes` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `customer_name` TEXT NOT NULL,
                    `customer_phone` TEXT NOT NULL DEFAULT '',
                    `order_id` INTEGER,
                    `order_public_id` TEXT,
                    `original_amount` INTEGER NOT NULL,
                    `paid_amount` INTEGER NOT NULL DEFAULT 0,
                    `status` TEXT NOT NULL DEFAULT 'OPEN',
                    `created_at` INTEGER NOT NULL,
                    `user_id` INTEGER,
                    `user_name` TEXT NOT NULL DEFAULT '',
                    `note` TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dettes_created_at` ON `dettes` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dettes_status` ON `dettes` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dettes_order_id` ON `dettes` (`order_id`)")

            // Table dette_paiements
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `dette_paiements` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `dette_id` INTEGER NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `paid_at` INTEGER NOT NULL,
                    `user_id` INTEGER,
                    `user_name` TEXT NOT NULL DEFAULT '',
                    `note` TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(`dette_id`) REFERENCES `dettes`(`id`) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dette_paiements_dette_id` ON `dette_paiements` (`dette_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dette_paiements_paid_at` ON `dette_paiements` (`paid_at`)")

            // Table avoirs
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `avoirs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `order_id` INTEGER,
                    `order_public_id` TEXT,
                    `customer_name` TEXT NOT NULL DEFAULT '',
                    `reason` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `user_id` INTEGER,
                    `user_name` TEXT NOT NULL DEFAULT '',
                    `note` TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_avoirs_created_at` ON `avoirs` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_avoirs_order_id` ON `avoirs` (`order_id`)")
        }
    }

    /** Sessions de caisse : ouverture/fermeture automatique à chaque login/logout. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `caisse_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `user_id` INTEGER NOT NULL,
                    `user_name` TEXT NOT NULL,
                    `opened_at` INTEGER NOT NULL,
                    `closed_at` INTEGER,
                    `sales_count` INTEGER NOT NULL DEFAULT 0,
                    `total_amount` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_caisse_sessions_user_id` ON `caisse_sessions` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_caisse_sessions_opened_at` ON `caisse_sessions` (`opened_at`)")
        }
    }

    /** Commandes maquis, users, tables, catégories, stock, audit, settings. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `categories` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `sort_order` INTEGER NOT NULL,
                    `is_active` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `users` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `pin` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `permissions` TEXT NOT NULL,
                    `is_active` INTEGER NOT NULL,
                    `is_waitress` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_name` ON `users` (`name`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `dining_tables` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `number` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `capacity` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `is_active` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dining_tables_number` ON `dining_tables` (`number`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `orders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `public_id` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `table_id` INTEGER,
                    `table_label` TEXT,
                    `waitress_id` INTEGER,
                    `waitress_name` TEXT,
                    `total_amount` INTEGER NOT NULL,
                    `paid_amount` INTEGER NOT NULL,
                    `note` TEXT,
                    `created_by_user_id` INTEGER,
                    `created_by_name` TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_orders_public_id` ON `orders` (`public_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_status` ON `orders` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_created_at` ON `orders` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_waitress_id` ON `orders` (`waitress_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_table_id` ON `orders` (`table_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `order_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `order_id` INTEGER NOT NULL,
                    `product_id` INTEGER NOT NULL,
                    `product_name` TEXT NOT NULL,
                    `category_name` TEXT NOT NULL,
                    `unit_price` INTEGER NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `line_total` INTEGER NOT NULL,
                    FOREIGN KEY(`order_id`) REFERENCES `orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_order_id` ON `order_items` (`order_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_product_id` ON `order_items` (`product_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_category_name` ON `order_items` (`category_name`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `order_payments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `order_id` INTEGER NOT NULL,
                    `payment_mode` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `amount_tendered` INTEGER NOT NULL,
                    `change_amount` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `user_id` INTEGER,
                    `user_name` TEXT,
                    FOREIGN KEY(`order_id`) REFERENCES `orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_payments_order_id` ON `order_payments` (`order_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_payments_created_at` ON `order_payments` (`created_at`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stock_movements` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `product_id` INTEGER NOT NULL,
                    `product_name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `previous_stock` INTEGER NOT NULL,
                    `new_stock` INTEGER NOT NULL,
                    `motif` TEXT NOT NULL,
                    `supplier` TEXT,
                    `comment` TEXT,
                    `user_id` INTEGER,
                    `user_name` TEXT,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_product_id` ON `stock_movements` (`product_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_created_at` ON `stock_movements` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_user_id` ON `stock_movements` (`user_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `audit_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `user_id` INTEGER,
                    `user_name` TEXT NOT NULL,
                    `action` TEXT NOT NULL,
                    `details` TEXT NOT NULL,
                    `old_value` TEXT,
                    `new_value` TEXT,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_created_at` ON `audit_logs` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_user_id` ON `audit_logs` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_action` ON `audit_logs` (`action`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `app_settings` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent(),
            )
        }
    }

    /** Dépenses (suivi mouvements). */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `expenses` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `description` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `category` TEXT,
                    `user_id` INTEGER,
                    `user_name` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_created_at` ON `expenses` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_user_id` ON `expenses` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_category` ON `expenses` (`category`)")
        }
    }

    /** Lignes produits sur les avoirs (+ type CASH/PRODUCT). */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `avoirs` ADD COLUMN `avoir_type` TEXT NOT NULL DEFAULT 'CASH'",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `avoir_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `avoir_id` INTEGER NOT NULL,
                    `product_id` INTEGER NOT NULL,
                    `product_name` TEXT NOT NULL,
                    `unit_price` INTEGER NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `line_total` INTEGER NOT NULL,
                    FOREIGN KEY(`avoir_id`) REFERENCES `avoirs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_avoir_items_avoir_id` ON `avoir_items` (`avoir_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_avoir_items_product_id` ON `avoir_items` (`product_id`)")
        }
    }

    /** Passation de relève (validation caissier suivant). */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `caisse_sessions` ADD COLUMN `handoff_status` TEXT")
            db.execSQL("ALTER TABLE `caisse_sessions` ADD COLUMN `validated_by_user_id` INTEGER")
            db.execSQL("ALTER TABLE `caisse_sessions` ADD COLUMN `validated_by_name` TEXT")
            db.execSQL("ALTER TABLE `caisse_sessions` ADD COLUMN `validated_at` INTEGER")
            db.execSQL("ALTER TABLE `caisse_sessions` ADD COLUMN `anomaly_note` TEXT")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_caisse_sessions_handoff_status` ON `caisse_sessions` (`handoff_status`)",
            )
        }
    }

    /** Produits sans suivi de stock + ventes par montant (quantités décimales). */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `products` ADD COLUMN `track_stock` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `products` ADD COLUMN `stock_open` REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `products` ADD COLUMN `content_per_unit` REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `products` ADD COLUMN `detail_price` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `products` ADD COLUMN `detail_unit` TEXT")
            db.execSQL("ALTER TABLE `order_items` ADD COLUMN `unit_fraction` REAL NOT NULL DEFAULT 1")
        }
    }

    /**
     * Retrait de la gestion des tables : supprime `dining_tables` et son réglage.
     * Rattrape aussi les colonnes de la v10 si la base v10 date d'une ancienne version.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "products", "track_stock", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "products", "stock_open", "REAL NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "products", "content_per_unit", "REAL NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "products", "detail_price", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "products", "detail_unit", "TEXT")
            addColumnIfMissing(db, "order_items", "unit_fraction", "REAL NOT NULL DEFAULT 1")
            db.execSQL("DROP INDEX IF EXISTS `index_dining_tables_number`")
            db.execSQL("DROP TABLE IF EXISTS `dining_tables`")
            db.execSQL("DELETE FROM `app_settings` WHERE `key` = 'tables_enabled'")
        }
    }

    /** Type d'emballage (carton, bidon…) des produits en vente libre. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "products", "stock_unit", "TEXT")
        }
    }

    private fun addColumnIfMissing(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
        definition: String,
    ) {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return
            }
        }
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
    }
}
