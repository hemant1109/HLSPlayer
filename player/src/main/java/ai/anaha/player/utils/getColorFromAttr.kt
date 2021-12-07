package ai.anaha.player.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.fragment.app.Fragment

fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true,
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

fun Fragment.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true,
): Int {
    requireContext().theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}