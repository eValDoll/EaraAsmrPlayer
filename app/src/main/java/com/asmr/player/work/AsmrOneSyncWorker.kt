package com.asmr.player.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.asmr.player.data.repository.AsmrOneAccountRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface AsmrOneSyncScheduling {
    fun enqueue(account: String)
    fun cancel(account: String)
}

class AsmrOneSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) : AsmrOneSyncScheduling {
    override fun enqueue(account: String) {
        val request = OneTimeWorkRequestBuilder<AsmrOneSyncWorker>()
            .setInputData(workDataOf(AsmrOneSyncWorker.ACCOUNT to account))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(account), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
    override fun cancel(account: String) { WorkManager.getInstance(context).cancelUniqueWork(workName(account)) }
    private fun workName(account: String) = "asmr-one-sync:$account"
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AsmrOneSyncEntryPoint {
    fun accountRepository(): AsmrOneAccountRepository
}

class AsmrOneSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val name = inputData.getString(ACCOUNT) ?: return Result.failure()
        val repository = EntryPointAccessors.fromApplication(applicationContext, AsmrOneSyncEntryPoint::class.java).accountRepository()
        return if (repository.drain(name)) Result.success() else if (runAttemptCount < 6) Result.retry() else Result.failure()
    }

    companion object { const val ACCOUNT = "account" }
}
