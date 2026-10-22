package dev.formhaus.demo.readme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.formhaus.demo.materialSymbol

val screenWidth = 393.dp
val screenHeight = 852.dp
val statusBarHeight = 40.dp
val navigationBarHeight = 24.dp
val frameWidth = 436.dp
val frameHeight = 898.dp
val systemBars = WindowInsets(top = statusBarHeight, bottom = navigationBarHeight)

private val bezel = 12.dp
private val rim = 1.5.dp
private val bodyRadius = 46.dp
private val screenRadius = 34.dp
private val bodyColor = Color(0xFF1B1B1F)
private val rimColor = Color(0xFF45454B)
private val systemContent = Color(0xFF1D1B20)

private val signal by lazy {
    materialSymbol(
        "signal_cellular_4_bar",
        "M177-80q-18 0-29.5-12T136-120q0-8 3-15t9-13l664-664q6-6 13-9t15-3q16 0 28 11.5t12 29.5v643q0 25-17.5 42.5T820-80H177Z",
    )
}
private val wifi by lazy {
    materialSymbol(
        "wifi",
        "M480-120q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29Zm0-440q75 0 142.5 24T745-470q20 15 " +
            "20.5 39.5T748-388q-17 17-42 17.5T661-384q-38-26-84-41t-97-15q-51 0-97 15t-84 41q-20 14-45 13t-42-18q-17-18-17-42.5t20-39.5" +
            "q55-42 " +
            "122.5-65.5T480-560Zm0-240q125 0 235.5 41T914-643q20 17 21 42t-17 43q-17 17-42 17.5T831-556q-72-59-161.5-91.5T480-680q-100 " +
            "0-189.5 32.5T129-556q-20 16-45 15.5T42-558q-18-18-17-43t21-42q88-75 198.5-116T480-800Z",
    )
}
private val battery by lazy {
    materialSymbol(
        "battery_full",
        "M320-80q-17 0-28.5-11.5T280-120v-640q0-17 11.5-28.5T320-800h80v-40q0-17 11.5-28.5T440-880h80q17 0 28.5 11.5T560-840v40h80q17 " +
            "0 28.5 11.5T680-760v640q0 17-11.5 28.5T640-80H320Z",
    )
}

@Composable
fun DeviceFrame(
    modifier: Modifier = Modifier,
    screen: @Composable () -> Unit,
) {
    Box(modifier.background(Color.White).size(frameWidth, frameHeight), contentAlignment = Alignment.Center) {
        SideButton(top = 190.dp, length = 64.dp)
        SideButton(top = 300.dp, length = 120.dp)
        Box(
            Modifier
                .size(screenWidth + bezel * 2, screenHeight + bezel * 2)
                .background(rimColor, RoundedCornerShape(bodyRadius))
                .padding(rim)
                .background(bodyColor, RoundedCornerShape(bodyRadius - rim))
                .padding(bezel - rim)
                .clip(RoundedCornerShape(screenRadius)),
        ) {
            Box(Modifier.size(screenWidth, screenHeight)) { screen() }
            StatusBar()
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .size(12.dp)
                    .background(Color.Black, CircleShape),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .size(108.dp, 4.dp)
                    .background(systemContent, CircleShape),
            )
        }
    }
}

@Composable
private fun SideButton(
    top: Dp,
    length: Dp,
) {
    Box(Modifier.fillMaxWidth().height(frameHeight)) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = -(frameWidth - screenWidth - bezel * 2) / 2 + 3.dp, y = top)
                .size(4.dp, length)
                .background(rimColor, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun StatusBar() {
    Row(
        Modifier.fillMaxWidth().height(statusBarHeight).padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("9:41", Modifier.weight(1f), color = systemContent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        StatusIcon(signal)
        StatusIcon(wifi)
        StatusIcon(battery)
    }
}

@Composable
private fun StatusIcon(icon: ImageVector) {
    Icon(icon, null, Modifier.size(17.dp), tint = systemContent)
}
