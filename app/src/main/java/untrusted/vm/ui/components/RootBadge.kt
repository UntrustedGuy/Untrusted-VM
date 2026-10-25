package untrusted.vm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import untrusted.vm.ui.theme.NeonGreen
import untrusted.vm.ui.theme.TextMuted

@Composable
fun RootBadge(
    isRootEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isRootEnabled) Color(0x2200E676) else Color(0x2264748B)
    val borderColor = if (isRootEnabled) NeonGreen else TextMuted
    val textColor = if (isRootEnabled) NeonGreen else TextMuted
    val text = if (isRootEnabled) "# ROOT" else "UNROOTED"

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
