package com.projectyaya.tvchannel

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/** True on an Android TV / Google TV box, false on a phone or tablet: picks the D-pad UI or the touch UI. */
fun isTelevision(context: Context): Boolean =
    (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
