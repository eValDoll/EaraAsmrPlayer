package com.asmr.player.ui.common;

import androidx.compose.foundation.lazy.layout.PrefetchRequest;
import androidx.compose.foundation.lazy.layout.PrefetchRequestScope;
import java.util.function.Consumer;

// Kotlin 的 PrefetchRequest 是 sealed 接口，Java 夹具用于执行真实调度器。
final class RecordingPrefetchRequest implements PrefetchRequest {
    private final Consumer<PrefetchRequestScope> onExecute;

    RecordingPrefetchRequest(Consumer<PrefetchRequestScope> onExecute) {
        this.onExecute = onExecute;
    }

    @Override
    public boolean execute(PrefetchRequestScope scope) {
        onExecute.accept(scope);
        return false;
    }
}
