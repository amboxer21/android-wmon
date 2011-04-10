/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class org_jnetpcap_winpcap_WinPcapSendQueue */

#ifndef _Included_org_jnetpcap_winpcap_WinPcapSendQueue
#define _Included_org_jnetpcap_winpcap_WinPcapSendQueue
#ifdef __cplusplus
extern "C" {
#endif
/* Inaccessible static: directMemory */
/* Inaccessible static: directMemorySoft */
#undef org_jnetpcap_winpcap_WinPcapSendQueue_MAX_DIRECT_MEMORY_DEFAULT
#define org_jnetpcap_winpcap_WinPcapSendQueue_MAX_DIRECT_MEMORY_DEFAULT 67108864LL
/* Inaccessible static: POINTER */
#undef org_jnetpcap_winpcap_WinPcapSendQueue_DEFAULT_QUEUE_SIZE
#define org_jnetpcap_winpcap_WinPcapSendQueue_DEFAULT_QUEUE_SIZE 65536L
/*
 * Class:     org_jnetpcap_winpcap_WinPcapSendQueue
 * Method:    sizeof
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_jnetpcap_winpcap_WinPcapSendQueue_sizeof
  (JNIEnv *, jclass);

/*
 * Class:     org_jnetpcap_winpcap_WinPcapSendQueue
 * Method:    getLen
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_jnetpcap_winpcap_WinPcapSendQueue_getLen
  (JNIEnv *, jobject);

/*
 * Class:     org_jnetpcap_winpcap_WinPcapSendQueue
 * Method:    getMaxLen
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_jnetpcap_winpcap_WinPcapSendQueue_getMaxLen
  (JNIEnv *, jobject);

/*
 * Class:     org_jnetpcap_winpcap_WinPcapSendQueue
 * Method:    incLen
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_org_jnetpcap_winpcap_WinPcapSendQueue_incLen
  (JNIEnv *, jobject, jint);

/*
 * Class:     org_jnetpcap_winpcap_WinPcapSendQueue
 * Method:    setBuffer
 * Signature: (Lorg/jnetpcap/nio/JBuffer;)V
 */
JNIEXPORT void JNICALL Java_org_jnetpcap_winpcap_WinPcapSendQueue_setBuffer
  (JNIEnv *, jobject, jobject);

/*
 * Class:     org_jnetpcap_winpcap_WinPcapSendQueue
 * Method:    setLen
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_org_jnetpcap_winpcap_WinPcapSendQueue_setLen
  (JNIEnv *, jobject, jint);

/*
 * Class:     org_jnetpcap_winpcap_WinPcapSendQueue
 * Method:    setMaxLen
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_org_jnetpcap_winpcap_WinPcapSendQueue_setMaxLen
  (JNIEnv *, jobject, jint);

#ifdef __cplusplus
}
#endif
#endif
