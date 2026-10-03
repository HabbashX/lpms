package com.lpms

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.lpms.ui.navigation.LpmsRoot
import dagger.hilt.android.AndroidEntryPoint

/**
 * Host activity. Extends [AppCompatActivity] rather than ComponentActivity so
 * [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales] can switch
 * the app language on API < 33 (it applies the locale to the activity).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LpmsRoot()
        }
    }
}
