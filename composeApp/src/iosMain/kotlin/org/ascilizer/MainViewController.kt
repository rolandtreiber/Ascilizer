package org.ascilizer

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    App()
}.also(::registerIosRootController)
