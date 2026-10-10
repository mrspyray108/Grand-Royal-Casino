package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.UserPreferences
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore repository responsible for handling complete CRUD operations on UserProfile data.
 * Adheres to zero-trust security, named database scoping, and structured error handling.
 */
class UserProfileRepository(
    private val db: FirebaseFirestore
) {
    /**
     * Secondary constructor: initializes FirebaseFirestore with the custom named database ID
     * from R.string.firestore_database_id.
     */
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    /**
     * Fail-fast assertion that an authenticated session exists.
     */
    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be authenticated before performing Firestore operations.")
    }

    /**
     * CREATE: Creates or sets the user profile in the /users/{userId} collection.
     */
    suspend fun createUserProfile(userProfile: UserProfile): Result<Unit> {
        val targetUid = userProfile.userId.ifBlank {
            userProfile.id.ifBlank { requireUserId() }
        }
        val docRef = db.collection("users").document(targetUid)
        return try {
            val profileToSave = userProfile.copy(
                id = targetUid,
                userId = targetUid,
                updatedAt = null // Triggers @ServerTimestamp
            )
            docRef.set(profileToSave, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * CREATE (Convenience): Creates an initial profile for a new user with default preferences
     * and welcome chip balance.
     */
    suspend fun createInitialProfile(
        userId: String,
        displayName: String,
        email: String = "",
        initialChipBalance: Long = 10_000L,
        preferences: UserPreferences = UserPreferences()
    ): Result<UserProfile> {
        val docRef = db.collection("users").document(userId)
        val newProfile = UserProfile(
            id = userId,
            userId = userId,
            displayName = displayName,
            username = displayName,
            chipBalance = initialChipBalance,
            balance = initialChipBalance,
            email = email,
            preferences = preferences,
            joinedTimestamp = System.currentTimeMillis()
        )
        return try {
            docRef.set(newProfile, SetOptions.merge()).await()
            Result.success(newProfile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * READ: Fetches the UserProfile document once for the specified userId.
     */
    suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                Result.success(null)
            } else {
                val profile = snapshot.toObject(UserProfile::class.java)
                Result.success(profile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * READ (Real-time Flow): Observes live updates for the UserProfile document.
     */
    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    /**
     * UPDATE: Updates an entire UserProfile object with latest data.
     */
    suspend fun updateUserProfile(userProfile: UserProfile): Result<Unit> {
        val targetUid = userProfile.userId.ifBlank {
            userProfile.id.ifBlank { requireUserId() }
        }
        val docRef = db.collection("users").document(targetUid)
        return try {
            val payload = userProfile.copy(
                id = targetUid,
                userId = targetUid,
                updatedAt = null // Triggers server timestamp refresh
            )
            docRef.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * UPDATE: Updates the user display name and username.
     */
    suspend fun updateDisplayName(userId: String, displayName: String): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            val updates = mapOf(
                "displayName" to displayName,
                "username" to displayName,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * UPDATE: Updates the current chip balance.
     */
    suspend fun updateChipBalance(userId: String, newBalance: Long): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            val updates = mapOf(
                "chipBalance" to newBalance,
                "balance" to newBalance,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * UPDATE: Atomically increments or decrements the virtual chip balance.
     */
    suspend fun incrementChipBalance(userId: String, delta: Long): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            val updates = mapOf(
                "chipBalance" to FieldValue.increment(delta),
                "balance" to FieldValue.increment(delta),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * UPDATE: Updates user preferences.
     */
    suspend fun updatePreferences(userId: String, preferences: UserPreferences): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            val updates = mapOf(
                "preferences" to preferences,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(updates, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * DELETE: Deletes the user profile document from Firestore.
     */
    suspend fun deleteUserProfile(userId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
