package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.InvoiceRepository
import com.example.ui.screens.AppNavigationRoot
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.InvoiceViewModel
import com.example.ui.viewmodel.InvoiceViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite Room database, DAOs, and repository context
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = InvoiceRepository(database.invoiceDao(), database.configDao(), database.activatedDeviceDao())

        // Create the state-holding ViewModel using standard Provider with factory
        val factory = InvoiceViewModelFactory(repository, applicationContext)
        val viewModel = ViewModelProvider(this, factory)[InvoiceViewModel::class.java]

        setContent {
            MyApplicationTheme {
                AppNavigationRoot(viewModel = viewModel)
            }
        }
    }
}
