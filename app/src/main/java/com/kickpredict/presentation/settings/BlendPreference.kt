package com.kickpredict.presentation.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How much weight the live predictions keep on the model vs the market price, in whole percent
 * (100 = pure model / no blend, 50 = the backtest-validated even blend). Backed by the shared
 * settings file so it's readable synchronously.
 */
class BlendPreference(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _modelWeightPercent = MutableStateFlow(preferences.getInt(KEY_WEIGHT, DEFAULT_WEIGHT))
    val modelWeightPercent: StateFlow<Int> = _modelWeightPercent.asStateFlow()

    fun setModelWeight(percent: Int) {
        if (_modelWeightPercent.value == percent) return
        preferences.edit().putInt(KEY_WEIGHT, percent).apply()
        _modelWeightPercent.value = percent
    }

    companion object {
        const val DEFAULT_WEIGHT = 50
        private const val FILE_NAME = "kick_predict_settings"
        private const val KEY_WEIGHT = "blend_model_weight"
    }
}
