package com.example.tiebasearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.tiebasearch.ui.search.SearchScreen
import com.example.tiebasearch.ui.theme.TiebaSearchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TiebaSearchTheme {
                SearchScreen()
            }
        }
    }
}
