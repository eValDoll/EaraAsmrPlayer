package com.asmr.player.performance

import android.os.Trace

/** 仅记录预热状态变化，供 Release trace 区分隐藏页准备与可见交互。 */
internal fun tracePrimaryPagePreloadEvent(event: String) {
    Trace.beginSection("primary.$event")
    Trace.endSection()
}
