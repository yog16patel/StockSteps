package org.example.stocksteps.presentation.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.stocksteps.theme.*

internal fun authColor(rgb: Int) = Color(0xFF000000L or rgb.toLong())
internal fun ThemeTextStyle.authStyle() = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = FontWeight(weight)
)

@Composable
internal fun AuthBrand() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.space10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AuthTokens.brandSize.dp)
                .background(authColor(AuthTokens.ink), RoundedCornerShape(AuthTokens.brandRadius.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("S", style = AuthTokens.logo.authStyle(), color = authColor(AuthTokens.surface))
        }
        Text("StockSteps", style = AuthTokens.brand.authStyle(), color = authColor(AuthTokens.ink))
    }
}

@Composable
internal fun AuthField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    password: Boolean = false,
    revealed: Boolean = false,
    onChange: (String) -> Unit,
    onReveal: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(AuthTokens.fieldGap.dp)) {
        Text(label, style = AuthTokens.label.authStyle(), color = authColor(AuthTokens.muted))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AuthTokens.controlHeight.dp)
                .background(authColor(AuthTokens.surface), RoundedCornerShape(AuthTokens.controlRadius.dp))
                .border(AuthTokens.borderWidth.dp, authColor(AuthTokens.outline), RoundedCornerShape(AuthTokens.controlRadius.dp))
                .padding(horizontal = ThemeSpacing.space14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = AuthTokens.body.authStyle().copy(color = authColor(AuthTokens.ink)),
                visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Email),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = ThemeSpacing.space14.dp)
                    .semantics { contentDescription = label },
                decorationBox = { input ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = AuthTokens.body.authStyle(), color = authColor(AuthTokens.muted))
                        input()
                    }
                }
            )
            if (password) TextButton(onClick = onReveal, enabled = enabled) {
                Text(if (revealed) "Hide" else "Show", style = AuthTokens.label.authStyle(), color = authColor(AuthTokens.link))
            }
        }
    }
}

@Composable
internal fun AuthButton(text: String, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = AuthTokens.controlHeight.dp),
        shape = RoundedCornerShape(AuthTokens.controlRadius.dp),
        border = if (primary) null else androidx.compose.foundation.BorderStroke(AuthTokens.borderWidth.dp, authColor(AuthTokens.outline)),
        colors = ButtonDefaults.buttonColors(
            containerColor = authColor(if (primary) AuthTokens.ink else AuthTokens.surface),
            contentColor = authColor(if (primary) AuthTokens.surface else AuthTokens.ink),
            disabledContainerColor = authColor(if (primary) AuthTokens.ink else AuthTokens.surface).copy(alpha = 0.5f),
            disabledContentColor = authColor(if (primary) AuthTokens.surface else AuthTokens.ink)
        )
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(ThemeSpacing.space10.dp))
        Text(text, style = AuthTokens.button.authStyle())
    }
}

@Composable
internal fun AuthDivider(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ThemeSpacing.space10.dp)) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = authColor(AuthTokens.outline))
        Text(text, style = AuthTokens.caption.authStyle(), color = authColor(AuthTokens.muted))
        HorizontalDivider(modifier = Modifier.weight(1f), color = authColor(AuthTokens.outline))
    }
}
