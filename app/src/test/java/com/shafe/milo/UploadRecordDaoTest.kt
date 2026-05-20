package com.shafe.milo

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32]) // Room testing with Robolectric
class UploadRecordDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: MiloDatabase
    private lateinit var dao: UploadRecordDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MiloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.uploadRecordDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetAllRecords() = runTest {
        val record = UploadRecord(
            localUri = "content://media/external/images/media/1",
            azureBlobId = "test_blob.jpg",
            confidence = 0.99,
            isMiloDetected = true,
            iterationName = "milo-v1"
        )

        dao.insert(record)

        dao.getAllRecords().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("test_blob.jpg", list[0].azureBlobId)
            assertEquals(0.99, list[0].confidence, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun recordsOrderedByTimestamp() = runTest {
        val record1 = UploadRecord(
            localUri = "uri1",
            azureBlobId = "blob1",
            confidence = 0.5,
            isMiloDetected = false,
            iterationName = "v1",
            timestamp = 1000L
        )
        val record2 = UploadRecord(
            localUri = "uri2",
            azureBlobId = "blob2",
            confidence = 0.6,
            isMiloDetected = true,
            iterationName = "v1",
            timestamp = 2000L
        )

        dao.insert(record1)
        dao.insert(record2)

        dao.getAllRecords().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals("blob2", list[0].azureBlobId) // Most recent first
            assertEquals("blob1", list[1].azureBlobId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setCorrectionUpdatesLabelAndStatus() = runTest {
        val record = UploadRecord(
            localUri = "uri1",
            azureBlobId = "blob1",
            confidence = 0.5,
            isMiloDetected = false,
            iterationName = "v1",
        )

        dao.insert(record)
        var id = -1L
        dao.getAllRecords().test {
            val list = awaitItem()
            id = list[0].id
            cancelAndIgnoreRemainingEvents()
        }

        dao.setCorrection(id, "milo", ReinforcementStatus.PENDING)
        val pending = dao.getRecordById(id)
        assertEquals("milo", pending?.correctedLabel)
        assertEquals(ReinforcementStatus.PENDING, pending?.reinforcementStatus)
        dao.setReinforcementSyncStatus(id, ReinforcementStatus.SYNCED, 1234L)

        val updated = dao.getRecordById(id)
        assertNotNull(updated)
        assertEquals("milo", updated?.correctedLabel)
        assertEquals(ReinforcementStatus.SYNCED, updated?.reinforcementStatus)
        assertEquals(1234L, updated?.reinforcementSyncedAt)
    }
}
