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

package com.bloomberg.selekt

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import kotlin.test.assertEquals

private const val POINTER = 42L
private const val DB = 43L
private const val INTERVAL_MILLIS = 2_000L

internal class SQLPreparedStatementTest {
    @Test
    fun stepWithRetryDone() {
        val sqlite = mock<SQLite>().apply {
            whenever(stepWithoutThrowing(any())) doReturn SQL_DONE
        }
        val statement = SQLPreparedStatement(POINTER, "BEGIN IMMEDIATE TRANSACTION", sqlite, CommonThreadLocalRandom)
        assertEquals(SQL_DONE, statement.step(INTERVAL_MILLIS))
    }

    @Test
    fun stepWithRetryRow() {
        val sqlite = mock<SQLite>().apply {
            whenever(stepWithoutThrowing(any())) doReturn SQL_ROW
        }
        val statement = SQLPreparedStatement(POINTER, "SELECT * FROM Foo", sqlite, CommonThreadLocalRandom)
        assertEquals(SQL_ROW, statement.step(INTERVAL_MILLIS))
    }

    @Test
    fun stepWithRetryExpires() {
        val sqlite = mock<SQLite>().apply {
            whenever(databaseHandle(any())) doReturn DB
            whenever(step(any())) doReturn SQL_BUSY
        }
        val statement = SQLPreparedStatement(POINTER, "BEGIN BLAH", sqlite, CommonThreadLocalRandom)
        assertThatExceptionOfType(Exception::class.java).isThrownBy {
            statement.step(0L)
        }
    }

    @Test
    fun stepWithRetryCanUltimatelySucceed() {
        val sqlite = mock<SQLite>().apply {
            whenever(stepWithoutThrowing(any())) doAnswer object : Answer<SQLCode> {
                private var count = 0

                override fun answer(invocation: InvocationOnMock) = when (count++) {
                    0 -> SQL_BUSY
                    else -> SQL_DONE
                }
            }
        }
        val statement = SQLPreparedStatement(POINTER, "BEGIN IMMEDIATE TRANSACTION", sqlite, CommonThreadLocalRandom)
        assertEquals(SQL_DONE, statement.step(500L))
    }

    @Test
    fun stepRetryDoesNotStackOverflow() {
        val sqlite = mock<SQLite>().apply {
            whenever(databaseHandle(any())) doReturn DB
            whenever(stepWithoutThrowing(any())) doReturn SQL_BUSY
        }
        val statement = SQLPreparedStatement(POINTER, "BEGIN BLAH", sqlite, CommonThreadLocalRandom)
        assertThatExceptionOfType(Exception::class.java).isThrownBy {
            statement.step(2_000L)
        }
    }

    @Test
    fun stepRejectsNegativeInterval() {
        val statement = SQLPreparedStatement(POINTER, "BEGIN BLAH", mock(), CommonThreadLocalRandom)
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            statement.step(-1L)
        }
    }
}
