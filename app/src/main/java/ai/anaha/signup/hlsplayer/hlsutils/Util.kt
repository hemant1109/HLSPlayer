package ai.anaha.signup.hlsplayer.hlsutils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes

object Util {

    fun getColorFromAttr(context: Context,
                         @AttrRes attrColor: Int,
                         typedValue: TypedValue = TypedValue(),
                         resolveRefs: Boolean = true,
    ): Int {
        context.theme.resolveAttribute(attrColor, typedValue, resolveRefs)
        return typedValue.data
    }
}