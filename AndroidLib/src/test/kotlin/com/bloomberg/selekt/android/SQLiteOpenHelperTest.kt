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

import android.content.Context
import com.bloomberg.commons.deleteDatabase
import com.bloomberg.selekt.SQLiteJournalMode
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.DisableOnDebug
import org.junit.rules.Timeout
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class SQLiteOpenHelperTest {
    @get:Rule
    val timeoutRule = DisableOnDebug(Timeout(10L, TimeUnit.SECONDS))

    private val file = File.createTempFile("test-open-helper", ".db").also { it.deleteOnExit() }

    private val targetContext = mock<Context>().apply {
        whenever(getDatabasePath(any())) doReturn file
    }
    private var databaseHelper: SQLiteOpenHelper? = null

    @After
    fun tearDown() {
        databaseHelper?.writableDatabase?.run {
            try {
                if (isOpen) {
                    close()
                }
                assertFalse(isOpen)
            } finally {
                assertTrue(deleteDatabase(file))
            }
        }
    }

    @Test
    fun zeroVersionThrows() {
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            createHelper(0, mock())
        }
    }

    @Test
    fun creation() {
        val callback = mock<ISQLiteOpenHelper.Callback>()
        createHelper(1, callback).also { databaseHelper = it }.use {
            val database = it.writableDatabase
            inOrder(callback) {
                verify(callback, times(1)).onConfigure(same(database))
                verify(callback, times(1)).onCreate(same(database))
            }
            assertEquals(1, database.version)
        }
    }

    @Test
    fun openWithMigrationCalls() {
        val callback = mock<ISQLiteOpenHelper.Callback>()
        createHelper(1, callback).also { databaseHelper = it }.use {
            val database = it.writableDatabase
            verify(callback, times(1)).onCreate(same(database))
            assertEquals(1, database.version)
        }
    }

    @Test
    fun secondOpenNoMigrationCalls() {
        val firstCallback = mock<ISQLiteOpenHelper.Callback>()
        createHelper(1, firstCallback).also { databaseHelper = it }.use {
            val database = it.writableDatabase
            verify(firstCallback, times(1)).onCreate(same(database))
            assertEquals(1, database.version)
        }
        val secondCallback = mock<ISQLiteOpenHelper.Callback>()
        createHelper(1, secondCallback).use {
            val database = it.writableDatabase
            verify(secondCallback, times(1)).onConfigure(same(database))
            verify(secondCallback, never()).onCreate(any())
            verify(secondCallback, never()).onDowngrade(any(), any(), any())
            verify(secondCallback, never()).onUpgrade(any(), any(), any())
            assertEquals(1, database.version)
        }
    }

    @Test
    fun upgrade() {
        val firstCallback = mock<ISQLiteOpenHelper.Callback>()
        createHelper(1, firstCallback).also { databaseHelper = it }.use {
            val database = it.writableDatabase
            verify(firstCallback, times(1)).onCreate(same(database))
            assertEquals(1, database.version)
        }
        val secondCallback = mock<ISQLiteOpenHelper.Callback>()
        createHelper(2, secondCallback).use {
            val database = it.writableDatabase
            inOrder(secondCallback) {
                verify(secondCallback, times(1)).onConfigure(same(database))
                verify(secondCallback, times(1)).onUpgrade(same(database), eq(1), eq(2))
            }
            verify(secondCallback, never()).onCreate(any())
            verify(secondCallback, never()).onDowngrade(any(), any(), any())
            assertEquals(2, database.version)
        }
    }

    @Test
    fun downgrade() {
        val firstCallback = mock<ISQLiteOpenHelper.Callback>()
        createHelper(2, firstCallback).also { databaseHelper = it }.use {
            val database = it.writableDatabase
            verify(firstCallback, times(1)).onCreate(same(database))
            assertEquals(2, database.version)
        }
        val secondCallback = mock<ISQLiteOpenHelper.Callback>()
        createHelper(1, secondCallback).use {
            val database = it.writableDatabase
            inOrder(secondCallback) {
                verify(secondCallback, times(1)).onConfigure(same(database))
                verify(secondCallback, times(1)).onDowngrade(same(database), eq(2), eq(1))
            }
            verify(secondCallback, never()).onCreate(any())
            verify(secondCallback, never()).onUpgrade(any(), any(), any())
            assertEquals(1, database.version)
        }
    }

    @Test
    fun journalModeDefaultWAL() {
        createHelper(1, mock()).use {
            assertSame(SQLiteJournalMode.WAL, it.writableDatabase.journalMode)
        }
    }

    @Test
    fun journalModeDelete() {
        createHelper(1, mock(), SQLiteOpenParams(journalMode = SQLiteJournalMode.DELETE)).use {
            assertSame(SQLiteJournalMode.DELETE, it.writableDatabase.journalMode)
        }
    }

    private fun createHelper(
        version: Int,
        callback: ISQLiteOpenHelper.Callback,
        openParams: SQLiteOpenParams = SQLiteOpenParams()
    ) = SQLiteOpenHelper(
        context = targetContext,
        configuration = ISQLiteOpenHelper.Configuration(
            callback = callback,
            key = ByteArray(32) { 0x42 },
            name = file.name
        ),
        openParams = openParams,
        version = version
    )
}
