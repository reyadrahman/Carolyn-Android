package com.siddhantkushwaha.carolyn;

import com.google.firebase.database.FirebaseDatabase;

public class FirebaseUtils {

    private static FirebaseDatabase firebaseDatabase;

    public static FirebaseDatabase getRealtimeDb(Boolean persistenceEnabled) {

        if (firebaseDatabase == null) {
            firebaseDatabase = FirebaseDatabase.getInstance();
            firebaseDatabase.setPersistenceEnabled(persistenceEnabled);
        }
        return firebaseDatabase;
    }
}