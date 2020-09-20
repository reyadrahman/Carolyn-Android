#include <jni.h>
#include <string>

using namespace std;

jstring string2jstring(JNIEnv *env, const string &cStr) {
    return env->NewStringUTF(cStr.c_str());
}

string jstring2string(JNIEnv *env, jstring jStr) {
    const char *cstr = env->GetStringUTFChars(jStr, nullptr);
    return string(cstr);
}