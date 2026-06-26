package com.brp.assistant.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration helpers.
 *
 * Wire into your AppDatabase builder:
 * ```
 *   Room.databaseBuilder(...)
 *       .addMigrations(*DbMigrationHelper.createMigrations())
 *       .fallbackToDestructiveMigrationFrom(1)   // safe fallback for early builds
 *       .build()
 * ```
 *
 * DO NOT use .fallbackToDestructiveMigration() in production —
 * explicit migrations preserve user data (selected vehicle, maintenance dates).
 */
object DbMigrationHelper {

    /** Keep in sync with AppDatabase.version */
    const val CURRENT_VERSION = 2

    /**
     * Schema v1 → v2: initial migration placeholder.
     * Fill in ALTER TABLE / CREATE TABLE statements when schema v2 is finalised.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // TODO: add concrete DDL when v2 schema is locked, e.g.:
            // db.execSQL("ALTER TABLE vehicles ADD COLUMN purchaseOdometer INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Returns all migrations in order for addMigrations(). */
    fun createMigrations(): Array<Migration> = arrayOf(
        MIGRATION_1_2
    )
}
