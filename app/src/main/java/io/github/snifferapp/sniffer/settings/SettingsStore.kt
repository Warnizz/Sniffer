package io.github.snifferapp.sniffer.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val apiKey: String? = null,
    val radiusM: Int = 2000,
    val ambientVolume: Float = 0.8f,
    val arVolume: Float = 0.3f,
    val arMuted: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object K {
        val apiKey = stringPreferencesKey("api_key")
        val radius = intPreferencesKey("radius_m")
        val ambientVolume = floatPreferencesKey("ambient_volume")
        val arVolume = floatPreferencesKey("ar_volume")
        val arMuted = booleanPreferencesKey("ar_muted")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            apiKey = p[K.apiKey]?.takeIf { it.isNotBlank() },
            radiusM = p[K.radius] ?: d.radiusM,
            ambientVolume = p[K.ambientVolume] ?: d.ambientVolume,
            arVolume = p[K.arVolume] ?: d.arVolume,
            arMuted = p[K.arMuted] ?: d.arMuted,
        )
    }

    suspend fun update(f: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = Settings(p[K.apiKey], p[K.radius] ?: 2000, p[K.ambientVolume] ?: 0.8f, p[K.arVolume] ?: 0.3f, p[K.arMuted] ?: false)
            val n = f(cur)
            if (n.apiKey.isNullOrBlank()) p.remove(K.apiKey) else p[K.apiKey] = n.apiKey.trim()
            p[K.radius] = n.radiusM
            p[K.ambientVolume] = n.ambientVolume
            p[K.arVolume] = n.arVolume
            p[K.arMuted] = n.arMuted
        }
    }
}
