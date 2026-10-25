package untrusted.vm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import untrusted.vm.ui.theme.*

@Composable
fun FloatingVmControls(
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(DarkSurfaceVariant.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
            .border(1.dp, DarkCardBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        IconButton(onClick = onHomeClick) {
            Icon(Icons.Default.Home, contentDescription = "Home", tint = NeonCyan)
        }
        IconButton(onClick = onTerminalClick) {
            Icon(Icons.Default.Code, contentDescription = "Terminal", tint = NeonGreen)
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
        }
    }
}
