package com.abdownloadmanager.shared.ui.widget

import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import ir.amirab.util.compose.resources.myStringResource

@Composable
fun AddUrlButton(
    modifier: Modifier=Modifier,
    onClick:()->Unit
) {
    val shape = myShapes.defaultRounded
    val addUrlIcon = MyIcons.link
    val downloadIcon = MyIcons.download
    Row(
        modifier
            .clip(shape)
            .background(myColors.primaryButtonGradient)
            .clickable(onClick = onClick)
            .height(32.dp)
//            .width(120.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,

        ) {
        WithContentColor(myColors.onPrimaryGradient) {
            MyIcon(addUrlIcon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                myStringResource(Res.string.new_download),
                Modifier,
                maxLines = 1,
                fontSize = myTextSizes.sm,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(myShapes.defaultRounded)
                .background(
                    Color.White.copy(alpha = 0.22f)
                ).padding(4.dp)
        ) {
            MyIcon(
                downloadIcon,
                null,
                Modifier.size(12.dp),
                tint = myColors.onPrimaryGradient,
            )
        }
    }

}
