package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.LedgerEntry
import com.example.data.model.ProvablyFairRound
import com.example.data.model.UserPreferences
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CasinoFirestoreRepository(
    private val db: FirebaseFirestore
) {
    private val userProfileRepo = UserProfileRepository(db)

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    // --- UserProfile CRUD Operations ---

    /**
     * CREATE: Saves or creates a full UserProfile model
     */
    suspend fun createUserProfile(userProfile: UserProfile): Result<Unit> =
        userProfileRepo.createUserProfile(userProfile)

    /**
     * READ: Fetches typed UserProfile once
     */
    suspend fun getUserProfile(userId: String): Result<UserProfile?> =
        userProfileRepo.getUserProfile(userId)

    /**
     * READ: Observes typed UserProfile in real-time
     */
    fun observeUserProfileTyped(userId: String): Flow<UserProfile?> =
        userProfileRepo.observeUserProfile(userId)

    /**
     * UPDATE: Updates UserProfile document
     */
    suspend fun updateUserProfile(userProfile: UserProfile): Result<Unit> =
        userProfileRepo.updateUserProfile(userProfile)

    /**
     * UPDATE: Updates chip balance
     */
    suspend fun updateChipBalance(userId: String, newBalance: Long): Result<Unit> =
        userProfileRepo.updateChipBalance(userId, newBalance)

    /**
     * UPDATE: Updates preferences
     */
    suspend fun updatePreferences(userId: String, preferences: UserPreferences): Result<Unit> =
        userProfileRepo.updatePreferences(userId, preferences)

    /**
     * DELETE: Deletes UserProfile document
     */
    suspend fun deleteUserProfile(userId: String): Result<Unit> =
        userProfileRepo.deleteUserProfile(userId)

    /**
     * Saves or updates user profile in Firestore
     */
    suspend fun saveUserProfile(
        userId: String,
        username: String,
        vipTier: String,
        balance: Long,
        totalBets: Long,
        totalWon: Long
    ): Result<Unit> {
        return try {
            val data = mapOf(
                "userId" to userId,
                "username" to username,
                "vipTier" to vipTier,
                "balance" to balance,
                "totalBets" to totalBets,
                "totalWon" to totalWon,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(userId)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of user profile data
     */
    fun observeUserProfile(userId: String): Flow<Map<String, Any>?> = callbackFlow {
        val docRef = db.collection("users").document(userId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.data)
        }
        awaitClose {
            listener.remove()
        }
    }

    /**
     * Records a ledger entry in user's personal ledger subcollection
     */
    suspend fun recordLedgerEntry(userId: String, entry: LedgerEntry): Result<Unit> {
        return try {
            val data = mapOf(
                "id" to entry.id,
                "type" to entry.type.name,
                "amount" to entry.amount,
                "balanceAfter" to entry.balanceAfter,
                "idempotencyKey" to entry.idempotencyKey,
                "note" to entry.note,
                "timestamp" to Timestamp(entry.timestamp / 1000, ((entry.timestamp % 1000) * 1_000_000).toInt())
            )
            db.collection("users").document(userId)
                .collection("ledger").document(entry.id)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Records a provably fair round to the public immutable rounds collection
     */
    suspend fun recordProvablyFairRound(round: ProvablyFairRound): Result<Unit> {
        return try {
            val data = mapOf(
                "roundId" to round.roundId,
                "gameType" to round.gameType.name,
                "serverSeedHashed" to round.serverSeedHashed,
                "clientSeed" to round.clientSeed,
                "nonce" to round.nonce,
                "outcomeHash" to round.outcomeHash,
                "summary" to round.outcomeSummary,
                "timestamp" to Timestamp(round.timestamp / 1000, ((round.timestamp % 1000) * 1_000_000).toInt())
            )
            db.collection("rounds").document(round.roundId)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
