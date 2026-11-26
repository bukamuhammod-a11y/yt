package com.example.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqPreset(
    val id: String,
    val name: String,
    val bandLevelsMb: List<Short>, // Band levels in millibels (mB)
    val bassBoostStrength: Short // 0 to 1000
)

class AudioEqualizerManager private constructor(context: Context) {

    companion object {
        private const val TAG = "AudioEqualizerManager"
        private const val PREFS_NAME = "audio_eq_prefs"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_PRESET_ID = "preset_id"
        private const val KEY_BASS_STRENGTH = "bass_strength"
        private const val KEY_CUSTOM_BANDS = "custom_bands"

        @Volatile
        private var instance: AudioEqualizerManager? = null

        fun getInstance(context: Context): AudioEqualizerManager {
            return instance ?: synchronized(this) {
                instance ?: AudioEqualizerManager(context.applicationContext).also { instance = it }
            }
        }

        // Standard EQ Presets requested by user
        val PRESETS = listOf(
            EqPreset(
                id = "ultra_bass",
                name = "Ultra Bass",
                bandLevelsMb = listOf(1200, 800, 0, 200, 400),
                bassBoostStrength = 1000
            ),
            EqPreset(
                id = "ultra_sad",
                name = "Ultra Sad",
                bandLevelsMb = listOf(-300, 200, 600, 800, 1000),
                bassBoostStrength = 200
            ),
            EqPreset(
                id = "bass_booster",
                name = "Bass Booster",
                bandLevelsMb = listOf(900, 600, 0, 0, 0),
                bassBoostStrength = 800
            ),
            EqPreset(
                id = "vocal_booster",
                name = "Vocal Booster",
                bandLevelsMb = listOf(-200, 0, 800, 600, 200),
                bassBoostStrength = 100
            ),
            EqPreset(
                id = "rock",
                name = "Rock",
                bandLevelsMb = listOf(700, 400, -200, 500, 800),
                bassBoostStrength = 600
            ),
            EqPreset(
                id = "pop",
                name = "Pop",
                bandLevelsMb = listOf(400, 200, 500, 300, -100),
                bassBoostStrength = 400
            ),
            EqPreset(
                id = "flat",
                name = "Flat",
                bandLevelsMb = listOf(0, 0, 0, 0, 0),
                bassBoostStrength = 0
            ),
            EqPreset(
                id = "custom",
                name = "Custom",
                bandLevelsMb = listOf(0, 0, 0, 0, 0),
                bassBoostStrength = 500
            )
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_EQ_ENABLED, true))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _selectedPresetId = MutableStateFlow(prefs.getString(KEY_PRESET_ID, "ultra_bass") ?: "ultra_bass")
    val selectedPresetId: StateFlow<String> = _selectedPresetId.asStateFlow()

    private val _bassStrength = MutableStateFlow(prefs.getInt(KEY_BASS_STRENGTH, 800).toShort())
    val bassStrength: StateFlow<Short> = _bassStrength.asStateFlow()

    // Current 5 band levels in dB (-15dB to +15dB range)
    private val _bandLevels = MutableStateFlow(loadBandLevels())
    val bandLevels: StateFlow<List<Float>> = _bandLevels.asStateFlow()

    // Frequencies of bands in Hz
    val bandFrequencies = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    init {
        initAudioEffects()
    }

    private fun loadBandLevels(): List<Float> {
        val savedCustom = prefs.getString(KEY_CUSTOM_BANDS, null)
        if (!savedCustom.isNullOrEmpty()) {
            try {
                return savedCustom.split(",").map { it.toFloat() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse saved bands", e)
            }
        }
        val initialPreset = PRESETS.find { it.id == _selectedPresetId.value } ?: PRESETS.first()
        return initialPreset.bandLevelsMb.map { it.toFloat() / 100f } // convert mB to dB
    }

    fun initAudioEffects(audioSessionId: Int = 0) {
        try {
            // Release prior effects
            equalizer?.release()
            bassBoost?.release()

            // Attach to global audio session 0 or specific session
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _isEnabled.value
            }

            try {
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = _isEnabled.value
                    if (strengthSupported) {
                        setStrength(_bassStrength.value)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost not supported on this device/ROM: ${e.message}")
            }

            applyCurrentSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Equalizer: ${e.message}")
        }
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling EQ enabled state", e)
        }
    }

    fun selectPreset(presetId: String) {
        _selectedPresetId.value = presetId
        prefs.edit().putString(KEY_PRESET_ID, presetId).apply()

        val preset = PRESETS.find { it.id == presetId } ?: return
        if (presetId != "custom") {
            val newLevels = preset.bandLevelsMb.map { it.toFloat() / 100f }
            _bandLevels.value = newLevels
            setBassStrength(preset.bassBoostStrength)
        }
        applyCurrentSettings()
    }

    fun setBandLevelDb(bandIndex: Int, levelDb: Float) {
        val current = _bandLevels.value.toMutableList()
        if (bandIndex in current.indices) {
            current[bandIndex] = levelDb.coerceIn(-15f, 15f)
            _bandLevels.value = current
            _selectedPresetId.value = "custom"
            prefs.edit().putString(KEY_PRESET_ID, "custom").apply()
            
            val serializedBands = current.joinToString(",")
            prefs.edit().putString(KEY_CUSTOM_BANDS, serializedBands).apply()

            applyCurrentSettings()
        }
    }

    fun setBassStrength(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        _bassStrength.value = clamped
        prefs.edit().putInt(KEY_BASS_STRENGTH, clamped.toInt()).apply()
        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(clamped)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting bass strength", e)
        }
    }

    private fun applyCurrentSettings() {
        val eq = equalizer ?: return
        if (!_isEnabled.value) return

        try {
            val numBands = eq.numberOfBands.toInt()
            val levelsMb = _bandLevels.value.map { (it * 100f).toInt().toShort() }

            for (i in 0 until numBands) {
                if (i in levelsMb.indices) {
                    val bandRange = eq.getBandLevelRange()
                    val targetLevel = levelsMb[i].coerceIn(bandRange[0], bandRange[1])
                    eq.setBandLevel(i.toShort(), targetLevel)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying band levels to hardware equalizer", e)
        }

        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(_bassStrength.value)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying bass boost", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizer = null
        bassBoost = null
    }
}
