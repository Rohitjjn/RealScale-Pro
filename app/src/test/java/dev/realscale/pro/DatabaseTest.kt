package dev.realscale.pro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.realscale.pro.core.database.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: AppDatabase
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    @Test fun creationDaoInsertsAndObserves() = runTest {
        db.creations().insert(CreationEntity(inputPath = "in.png", outputPath = "out.png", modelName = "DF2K", scale = 4, createdAt = 1, durationMs = 10))
        val all = db.creations().observeAll().first()
        assertEquals(1, all.size); assertEquals("DF2K", all[0].modelName)
    }

    @Test fun modelDaoUpsertAndDelete() = runTest {
        db.models().upsert(ModelEntity("a", "A", "Real-ESRGAN", 4, "/m/a", "sha", 1))
        assertEquals("A", db.models().get("a")?.name)
        db.models().delete("a")
        assertNull(db.models().get("a"))
    }
}
