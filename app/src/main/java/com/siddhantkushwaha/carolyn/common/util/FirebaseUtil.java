package com.siddhantkushwaha.carolyn.common.util;

import com.google.firebase.database.FirebaseDatabase;


public class FirebaseUtil {

    private static FirebaseDatabase firebaseDatabase;

    public static FirebaseDatabase getRealtimeDb(Boolean persistenceEnabled) {

        if (firebaseDatabase == null) {
            firebaseDatabase = FirebaseDatabase.getInstance();
            firebaseDatabase.setPersistenceEnabled(persistenceEnabled);
        }
        return firebaseDatabase;
    }
}