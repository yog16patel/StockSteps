package org.example.stocksteps

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource

@Composable
internal fun AndroidBackIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_back_button),
        contentDescription = "Back"
    )
}
