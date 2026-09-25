package com.semhas.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.semhas.app.utils.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.billingDataStore: DataStore<Preferences> by preferencesDataStore(name = "semhas_billing_prefs")

interface BillingRateStore {
    val rateFlow: Flow<Double>
    suspend fun getSavedRate(): Double
    suspend fun saveRate(rate: Double)
}

class DataStoreBillingPreferences(
    private val context: Context
) : BillingRateStore {

    companion object {
        val KEY_LEGACY_RATE = doublePreferencesKey("electricity_rate")
        val KEY_ELECTRICITY_RATE_WH = doublePreferencesKey("electricity_rate_wh")
        val KEY_RATE_MIGRATED_VERSION = booleanPreferencesKey("rate_migrated_to_wh_v1")
    }

    override val rateFlow: Flow<Double> = context.billingDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val isMigrated = preferences[KEY_RATE_MIGRATED_VERSION] ?: false
            if (isMigrated) {
                preferences[KEY_ELECTRICITY_RATE_WH] ?: Constants.DEFAULT_ELECTRICITY_RATE_PER_WH
            } else {
                val legacy = preferences[KEY_LEGACY_RATE]
                if (legacy != null && legacy > 0.0) {
                    legacy / 1000.0
                } else {
                    Constants.DEFAULT_ELECTRICITY_RATE_PER_WH
                }
            }
        }

    override suspend fun getSavedRate(): Double {
        return try {
            var resolvedRate = Constants.DEFAULT_ELECTRICITY_RATE_PER_WH
            context.billingDataStore.edit { preferences ->
                val isMigrated = preferences[KEY_RATE_MIGRATED_VERSION] ?: false
                if (!isMigrated) {
                    val legacyRate = preferences[KEY_LEGACY_RATE]
                    if (legacyRate != null && legacyRate > 0.0) {
                        // Migrate legacy kWh rate to Wh rate exactly once
                        val migratedRate = legacyRate / 1000.0
                        preferences[KEY_ELECTRICITY_RATE_WH] = migratedRate
                        preferences[KEY_RATE_MIGRATED_VERSION] = true
                        resolvedRate = migratedRate
                    } else {
                        // Clean install or no legacy rate
                        preferences[KEY_ELECTRICITY_RATE_WH] = Constants.DEFAULT_ELECTRICITY_RATE_PER_WH
                        preferences[KEY_RATE_MIGRATED_VERSION] = true
                        resolvedRate = Constants.DEFAULT_ELECTRICITY_RATE_PER_WH
                    }
                } else {
                    resolvedRate = preferences[KEY_ELECTRICITY_RATE_WH] ?: Constants.DEFAULT_ELECTRICITY_RATE_PER_WH
                }
            }
            resolvedRate
        } catch (e: Exception) {
            Constants.DEFAULT_ELECTRICITY_RATE_PER_WH
        }
    }

    override suspend fun saveRate(rate: Double) {
        if (rate <= 0.0) return
        try {
            context.billingDataStore.edit { preferences ->
                preferences[KEY_ELECTRICITY_RATE_WH] = rate
                preferences[KEY_RATE_MIGRATED_VERSION] = true
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }
}

/**
 * In-memory fallback / test implementation of BillingRateStore
 */
class InMemoryBillingRateStore(
    initialRate: Double = Constants.DEFAULT_ELECTRICITY_RATE_PER_WH,
    migrated: Boolean = false
) : BillingRateStore {
    private var isMigrated = migrated
    private var currentRate: Double

    init {
        currentRate = if (!isMigrated && initialRate > 1.0) {
            // Migrate legacy rate > 1.0 (e.g. 8.0, 10.5) once
            isMigrated = true
            initialRate / 1000.0
        } else {
            isMigrated = true
            initialRate
        }
    }

    private val _rateFlow = MutableStateFlow(currentRate)
    override val rateFlow: Flow<Double> = _rateFlow

    override suspend fun getSavedRate(): Double = currentRate

    override suspend fun saveRate(rate: Double) {
        if (rate <= 0.0) return
        currentRate = rate
        isMigrated = true
        _rateFlow.value = rate
    }
}
