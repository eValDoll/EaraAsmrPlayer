package com.asmr.player.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase

internal fun removeManualCoverColumnForLegacySchema(database: SupportSQLiteDatabase) {
    removeTagMetadataColumnsForLegacySchema(database)
    removeColumnForLegacySchema(database, "albums", "manualCoverPath")
}

internal fun removeTagMetadataColumnsForLegacySchema(database: SupportSQLiteDatabase) {
    removeColumnForLegacySchema(database, "albums", "tagDetailsJson")
    removeColumnForLegacySchema(database, "download_tasks", "albumTagDetailsJson")
}

private fun removeColumnForLegacySchema(database: SupportSQLiteDatabase, table: String, column: String) {
    val createSql = database.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = '$table'").use {
        check(it.moveToFirst())
        it.getString(0)
    }
    val manualColumn = Regex(
        """,\s*[`"]?${column}[`"]?\s+TEXT\s+NOT\s+NULL\s+DEFAULT\s+'[^']*'""",
        RegexOption.IGNORE_CASE,
    )
    check(manualColumn.findAll(createSql).count() == 1)
    val legacyCreateSql = createSql.replace(manualColumn, "").replaceFirst(
        Regex("""CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:`${table}`|"${table}"|${table})""", RegexOption.IGNORE_CASE),
        "CREATE TABLE `legacy_${table}`",
    )
    val columns = database.query("PRAGMA table_info($table)").use { cursor ->
        buildList {
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                if (name != column) add("`${name.replace("`", "``")}`")
            }
        }.joinToString(", ")
    }
    val dependentSql = database.query(
        "SELECT sql FROM sqlite_master WHERE tbl_name = '$table' AND type IN ('index', 'trigger') AND sql IS NOT NULL"
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }
    val foreignKeysEnabled = database.query("PRAGMA foreign_keys").use {
        it.moveToFirst() && it.getInt(0) != 0
    }
    database.setForeignKeyConstraintsEnabled(false)
    try {
        database.beginTransaction()
        try {
            database.execSQL(legacyCreateSql)
            database.execSQL("INSERT INTO legacy_${table} ($columns) SELECT $columns FROM $table")
            database.execSQL("DROP TABLE $table")
            database.execSQL("ALTER TABLE legacy_${table} RENAME TO $table")
            dependentSql.forEach(database::execSQL)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    } finally {
        database.setForeignKeyConstraintsEnabled(foreignKeysEnabled)
    }
}
