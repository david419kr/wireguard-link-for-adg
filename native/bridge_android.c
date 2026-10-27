// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

#include <jni.h>
#include <stdlib.h>
#include <unistd.h>
#include "_cgo_export.h"

#define JNI(name) Java_com_adguard_vpn_wireguard_NativeEngine_##name
static jstring string_result(JNIEnv *env,char *result) {
    if (!result) return NULL;
    jstring s=(*env)->NewStringUTF(env,result);
    free(result);
    return s;
}

JNIEXPORT jlong JNICALL JNI(start)(JNIEnv *env,jclass klass,jstring config,jstring addresses,jstring dns,jint mtu,jint fd) {
    if (!config || !addresses || !dns) {if(fd>=0)close(fd);return 0;}
    const char *c=(*env)->GetStringUTFChars(env,config,NULL);
    const char *a=(*env)->GetStringUTFChars(env,addresses,NULL);
    const char *d=(*env)->GetStringUTFChars(env,dns,NULL);
    jlong result=0;
    if (c && a && d) result=wgStart((char *)c,(char *)a,(char *)d,mtu,fd);
    else if(fd>=0)close(fd);
    if(c)(*env)->ReleaseStringUTFChars(env,config,c);
    if(a)(*env)->ReleaseStringUTFChars(env,addresses,a);
    if(d)(*env)->ReleaseStringUTFChars(env,dns,d);
    return result;
}
JNIEXPORT jstring JNICALL JNI(startSocks)(JNIEnv *env,jclass klass,jlong handle,jstring host,jint port,jstring username,jstring password,jstring allowed) {
    if(!host || !username || !password || !allowed) return (*env)->NewStringUTF(env,"Missing local proxy settings");
    const char *h=(*env)->GetStringUTFChars(env,host,NULL);
    const char *u=(*env)->GetStringUTFChars(env,username,NULL);
    const char *p=(*env)->GetStringUTFChars(env,password,NULL);
    const char *a=(*env)->GetStringUTFChars(env,allowed,NULL);
    char *result=NULL;
    if(h && u && p && a) result=wgStartSocks(handle,(char *)h,port,(char *)u,(char *)p,(char *)a);
    if(h)(*env)->ReleaseStringUTFChars(env,host,h);
    if(u)(*env)->ReleaseStringUTFChars(env,username,u);
    if(p)(*env)->ReleaseStringUTFChars(env,password,p);
    if(a)(*env)->ReleaseStringUTFChars(env,allowed,a);
    return string_result(env,result);
}
JNIEXPORT void JNICALL JNI(stop)(JNIEnv *env,jclass klass,jlong handle) {wgStop(handle);}
JNIEXPORT jstring JNICALL JNI(statistics)(JNIEnv *env,jclass klass,jlong handle) {return string_result(env,wgStats(handle));}
JNIEXPORT jstring JNICALL JNI(lastError)(JNIEnv *env,jclass klass) {return string_result(env,wgLastError());}
JNIEXPORT jintArray JNICALL JNI(sockets)(JNIEnv *env,jclass klass,jlong handle) {
    jint fds[]={wgSocket(handle,4),wgSocket(handle,6)};
    jintArray result=(*env)->NewIntArray(env,2);
    if(result)(*env)->SetIntArrayRegion(env,result,0,2,fds);
    return result;
}
JNIEXPORT jboolean JNICALL JNI(networkChanged)(JNIEnv *env,jclass klass,jlong handle) {return wgNetworkChanged(handle)?JNI_TRUE:JNI_FALSE;}
