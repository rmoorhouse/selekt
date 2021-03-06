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
import android.database.sqlite.SQLiteException
import com.bloomberg.commons.deleteDatabase
import com.bloomberg.selekt.SQLiteJournalMode
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.DisableOnDebug
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun createSQLiteOpenHelper(
    context: Context,
    inputs: TransactionTestInputs
): ISQLiteOpenHelper = SQLiteOpenHelper(
    context,
    ISQLiteOpenHelper.Configuration(
        callback = object : ISQLiteOpenHelper.Callback {
            override fun onCreate(database: SQLiteDatabase) = database.run {
                exec("CREATE TABLE 'Foo' (bar INT)")
            }

            override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        },
        key = null,
        name = "test-transactions"
    ),
    1,
    SQLiteOpenParams(inputs.journalMode)
)

internal data class TransactionTestInputs(
    val journalMode: SQLiteJournalMode
) {
    override fun toString() = "$journalMode"
}

@RunWith(Parameterized::class)
internal class SQLiteDatabaseTransactionTest(inputs: TransactionTestInputs) {
    @get:Rule
    val timeoutRule = DisableOnDebug(Timeout(10L, TimeUnit.SECONDS))

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun initParameters(): Iterable<TransactionTestInputs> = arrayOf(
            SQLiteJournalMode.DELETE,
            SQLiteJournalMode.WAL
        ).map { TransactionTestInputs(it) }
    }

    private val file = File.createTempFile("test-see-transactions", ".db").also { it.deleteOnExit() }

    private val targetContext = mock<Context>().apply {
        whenever(getDatabasePath(any())) doReturn file
    }
    private val databaseHelper = createSQLiteOpenHelper(targetContext, inputs)

    @After
    fun tearDown() {
        databaseHelper.writableDatabase.run {
            try {
                close()
                assertFalse(isOpen)
            } finally {
                assertTrue(deleteDatabase(file))
            }
        }
    }

    @Test
    fun isConnectionHeldByCurrentThreadInTransaction() = databaseHelper.writableDatabase.transact {
        assertTrue(isConnectionHeldByCurrentThread)
    }

    @Test
    fun isConnectionNotHeldByCurrentThreadOutsideTransaction() = databaseHelper.writableDatabase.run {
        assertFalse(isConnectionHeldByCurrentThread)
    }

    @Test
    fun isTransactionOpenedByCurrentThreadInTransaction() = databaseHelper.writableDatabase.transact {
        assertTrue(isTransactionOpenedByCurrentThread)
    }

    @Test
    fun isTransactionNotOpenedByCurrentThreadOutsideTransaction() = databaseHelper.writableDatabase.run {
        assertFalse(isTransactionOpenedByCurrentThread)
    }

    @Test
    fun setImmediateTransactionSuccessful() = databaseHelper.writableDatabase.run {
        try {
            beginImmediateTransaction()
            assertTrue(isTransactionOpenedByCurrentThread)
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
        assertFalse(isTransactionOpenedByCurrentThread)
    }

    @Test
    fun setExclusiveTransactionSuccessful() = databaseHelper.writableDatabase.run {
        try {
            beginExclusiveTransaction()
            assertTrue(isTransactionOpenedByCurrentThread)
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
        assertFalse(isTransactionOpenedByCurrentThread)
    }

    @Test(expected = SQLiteException::class)
    fun vacuumInsideTransaction() = databaseHelper.writableDatabase.transact { vacuum() }

    @Test(expected = IllegalStateException::class)
    fun setJournalModeInsideTransaction(): Unit = databaseHelper.writableDatabase.transact {
        setJournalMode(if (SQLiteJournalMode.WAL == journalMode) SQLiteJournalMode.DELETE else SQLiteJournalMode.WAL)
    }

    @Test(expected = IllegalStateException::class)
    fun setForeignKeyConstraintsEnabledInsideTransaction() = databaseHelper.writableDatabase.transact {
        setForeignKeyConstraintsEnabled(true)
    }

    @Test(expected = IllegalStateException::class)
    fun setForeignKeyConstraintsDisabledInsideTransaction() = databaseHelper.writableDatabase.transact {
        setForeignKeyConstraintsEnabled(false)
    }
}
