package com.asmr.player.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase

internal fun removeManualCoverColumnForLegacySchema(database: SupportSQLiteDatabase) {
    val createSql = database.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'albums'").use {
        check(it.moveToFirst())
        it.getString(0)
    }
    val manualColumn = Regex(
        """,\s*[`"]?manualCoverPath[`"]?\s+TEXT\s+NOT\s+NULL\s+DEFAULT\s+''""",
        RegexOption.IGNORE_CASE,
    )
    check(manualColumn.findAll(createSql).count() == 1)
    val legacyCreateSql = createSql.replace(manualColumn, "").replaceFirst(
        Regex("""CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:`albums`|"albums"|albums)""", RegexOption.IGNORE_CASE),
        "CREATE TABLE `legacy_albums`",
    )
    val columns = database.query("PRAGMA table_info(albums)").use { cursor ->
        buildList {
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                if (name != "manualCoverPath") add("`${name.replace("`", "``")}`")
            }
        }.joinToString(", ")
    }
    val dependentSql = database.query(
        "SELECT sql FROM sqlite_master WHERE tbl_name = 'albums' AND type IN ('index', 'trigger') AND sql IS NOT NULL"
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
            database.execSQL("INSERT INTO legacy_albums ($columns) SELECT $columns FROM albums")
            database.execSQL("DROP TABLE albums")
            database.execSQL("ALTER TABLE legacy_albums RENAME TO albums")
            dependentSql.forEach(database::execSQL)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    } finally {
        database.setForeignKeyConstraintsEnabled(foreignKeysEnabled)
    }
}
