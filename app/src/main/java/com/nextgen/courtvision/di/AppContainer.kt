package com.nextgen.courtvision.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nextgen.courtvision.data.repository.AuthRepository
import com.nextgen.courtvision.data.repository.FirebaseAuthRepository
import com.nextgen.courtvision.data.repository.FirebaseFirestoreRepository
import com.nextgen.courtvision.data.repository.FirestoreRepository

/**
 * Manual dependency container (constructor injection without a DI framework).
 * ViewModels receive repository interfaces, never Firebase types directly,
 * so every dependency can be replaced with a mock in unit tests.
 */
class AppContainer {

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
    }

    val firestoreRepository: FirestoreRepository by lazy {
        FirebaseFirestoreRepository(FirebaseFirestore.getInstance())
    }
}
