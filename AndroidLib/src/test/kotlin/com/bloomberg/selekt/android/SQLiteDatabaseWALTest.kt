/*
 * Copyright 2020 Bloomberg Finance L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bloomberg.selekt.android

import android.content.ContentValues
import android.database.sqlite.SQLiteException
import com.bloomberg.selekt.SQLiteAutoVacuumMode
import com.bloomberg.selekt.SQLiteJournalMode
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
internal class SQLiteDatabaseWALTest {
    private val file = File.createTempFile("test-sql-database-wal", ".db").also { it.deleteOnExit() }

    private val database = SQLiteDatabase.openOrCreateDatabase(file, SQLiteJournalMode.WAL.databaseConfiguration,
        ByteArray(32) { 0x42 })

    @Before
    fun setUp() {
        database.exec("PRAGMA journal_mode=${SQLiteJournalMode.WAL}")
    }

    @Test
    fun journalMode(): Unit = database.run {
        assertEquals(SQLiteJournalMode.WAL, journalMode)
    }

    @Test
    fun maximumPageCount(): Unit = database.run {
        setMaxPageCount(42L)
        assertEquals(42L, maxPageCount)
    }

    @Test
    fun pageCount(): Unit = database.run {
        assertEquals(1, pageCount)
    }

    @Test
    fun pageSizeDefault(): Unit = database.run {
        assertEquals(4_096L, pageSize)
    }

    @Test
    fun setPageSize(): Unit = database.run {
        setPageSizeExponent(16)
        vacuum()
        assertEquals(4_096L, pageSize)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setPageSizeThrows(): Unit = database.run {
        setPageSizeExponent(17)
    }

    @Test
    fun integrityCheckMain(): Unit = database.run {
        assertTrue(integrityCheck("main"))
    }

    @Test
    fun integrityCheckIllegal(): Unit = database.run {
        assertThatExceptionOfType(SQLiteException::class.java).isThrownBy {
            integrityCheck("foo")
        }
    }

    @Test
    fun vacuum(): Unit = database.run {
        exec("CREATE TABLE 'Foo' (bar INT)")
        insert("Foo", ContentValues().apply { put("bar", 42) }, ConflictAlgorithm.REPLACE)
        delete("Foo", null, null)
        vacuum()
        assertEquals(SQLiteJournalMode.WAL, journalMode)
    }

    @Test
    fun fullAutoVacuum(): Unit = database.run {
        autoVacuum = SQLiteAutoVacuumMode.FULL
        vacuum()
        assertSame(SQLiteAutoVacuumMode.FULL, autoVacuum)
    }

    @Test
    fun incrementalAutoVacuum(): Unit = database.run {
        assertSame(SQLiteAutoVacuumMode.INCREMENTAL, autoVacuum)
    }

    @Test
    fun noneAutoVacuum(): Unit = database.run {
        autoVacuum = SQLiteAutoVacuumMode.NONE
        vacuum()
        assertSame(SQLiteAutoVacuumMode.NONE, autoVacuum)
    }

    @Test
    fun secureDeleteIsFast(): Unit = database.run {
        query("PRAGMA secure_delete", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
    }

    @Test
    fun version() = database.run {
        version = 42
        assertEquals(42, version)
    }
}
