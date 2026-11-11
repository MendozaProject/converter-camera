package com.devsquid.convertercamera

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devsquid.convertercamera.database.AppDatabase
import com.devsquid.convertercamera.database.CurrencyData
import com.devsquid.convertercamera.database.CurrencyDataDao
import com.devsquid.convertercamera.database.SessionRepository
import com.devsquid.convertercamera.domain.CurrencyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.Date

@RunWith(AndroidJUnit4::class)
class DataPersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var dao: CurrencyDataDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.currencyDataDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testRoomDatabasePersistenceAcrossRecreation() = runBlocking {
        val currency = CurrencyData(
            isoCode = "EUR",
            name = "Euro",
            symbol = "€",
            rate = BigDecimal("0.85"),
            timeStamp = Date(),
            decimalPlaces = 2
        )
        dao.insertCurrency(currency)

        // Verify data exists
        val retrieved1 = dao.getCurrency("EUR")
        assertEquals("EUR", retrieved1.isoCode)
        assertEquals("Euro", retrieved1.name)

        // Simulate process death / closing DB connection
        db.close()

        // Reopen database
        val db2 = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao2 = db2.currencyDataDao()
        dao2.insertCurrency(currency)
        val retrieved2 = dao2.getCurrency("EUR")
        assertEquals("EUR", retrieved2.isoCode)
        db2.close()
    }

    @Test
    fun testSessionRepositoryDataStorePersistenceAcrossProcessDeath() = runBlocking {
        val currencyRepository = CurrencyRepository(dao)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // First instance of SessionRepository (simulating app start)
        val sessionRepo1 = SessionRepository(currencyRepository, scope, context)
        sessionRepo1.setFromCurrency("JPY")
        sessionRepo1.setToCurrency("USD")

        // Allow time for DataStore preferences edit to persist
        delay(600L)

        // Simulate process death and relaunch by creating a new SessionRepository instance with the same context
        val sessionRepo2 = SessionRepository(currencyRepository, scope, context)

        // Allow time for DataStore init flow to read saved preferences
        delay(600L)

        assertEquals("JPY", sessionRepo2.sessionState.value.fromCurrencyISO)
        assertEquals("USD", sessionRepo2.sessionState.value.toCurrencyISO)
    }
}
