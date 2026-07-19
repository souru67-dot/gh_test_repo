package com.souru.colorhunt.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.souru.colorhunt.AppContainer
import com.souru.colorhunt.ColorHuntApplication

/** Pulls the app's DI container out of the ViewModel [CreationExtras]. */
fun CreationExtras.appContainer(): AppContainer =
    (this[APPLICATION_KEY] as ColorHuntApplication).container
