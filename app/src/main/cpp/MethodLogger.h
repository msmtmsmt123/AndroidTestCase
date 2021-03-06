#pragma once

#include <jni.h>
#include "dalvik/object.h"
#include <string>
#include <list>

extern jclass g_javaClass;
extern jclass g_ObjectClass;
extern jclass g_MethodClass;
extern jclass g_javaFieldClass;
extern jclass g_javaMemberClass;
extern jmethodID g_getNameOfClass;
extern jmethodID g_getDeclaredMethods;
extern jmethodID g_getSignature;
extern jmethodID g_getNameOfMethod;
extern jmethodID g_getParameterTypes;
extern jmethodID g_getReturnType;
extern jmethodID g_getDeclaredField;
extern jmethodID g_getInt;
extern jmethodID g_getModifiers;
extern jmethodID g_toString;

extern std::list<std::string>   g_lstExcludedClassName;
extern std::list<std::string>   g_lstIncludedClassName;

void nativeFunc_OnCallNatvieMethod(const u4 *args, JValue *pResult, const Method *method, void *self);
void nativeFunc_OnCallJavaMethod(const u4 *args, JValue *pResult, const Method *method, void *self);
bool dalvik_hook_java_method(Method *method, const char *szClassName, const char *szMethodName, const char *szSig, const char *szDesc, ArrayObject * pParamTypes, ClassObject *pRetType);
bool dalvik_hook_native_method(Method *method, const char *szClassName, const char *szMethodName, const char *szSig, const char *szDesc, ArrayObject * pParamTypes, ClassObject *pRetType);
bool dalvik_hook_method(Method *method, const char *szClassName, const char *szMethodName, const char *szSig, const char *szDesc, ArrayObject * pParamTypes, ClassObject *pRetType);
///
/// \param jni
/// \param clazz 是java/lang/ClassLoader返回的jclass，本函数不负责释放
void hook_loadClass(JNIEnv *jni,jclass clazz);

void enumAllMethodOfClass(JNIEnv *jni, jclass clazz, const std::string &sClassName);

class CMethodLogger {
public:
    CMethodLogger();
    ~CMethodLogger();

public:
    static bool start(JNIEnv *jni);
    static void addExcludedClassName(const char * lpszName);
    static void addIncludedClassName(const char * lpszName);
    static bool isCanHookThisClass(const std::string&sClassName);
    static bool isCanHookThisMethod(const char*szName, const char*szRetType, const char*szParam, const char*szSig);
private:
    static void hookByforName(JNIEnv *jni);
    static void hookByloadClass(JNIEnv *jni);
    static void hookBydvmResolveClass(JNIEnv *jni);

private:
    static void init(JNIEnv *jni);
};

