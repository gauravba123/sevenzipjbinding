#include "StdAfx.h"

#include "jnitools.h"
#include "JavaSequentialInStream.h"


void JavaSequentialInStream::Init()
{
	char classname[1024];
	
	this->ReadMethodID = env->GetMethodID(javaImplementationClass, "read", "([B[I)I");
	
	FATALIF1(this->ReadMethodID == NULL, "'int read(byte [], int [])' method in class %s not found!",
			GetJavaClassName(env, javaImplementationClass, classname, sizeof(classname)));
}

STDMETHODIMP JavaSequentialInStream::Read(void *data, UInt32 size, UInt32 *processedSize)
{
	jbyteArray byteArray = env->NewByteArray(size);
	FATALIF(byteArray == NULL, "Out of local resource of out of memory: byteArray == NULL")
	
	jintArray intArray = env->NewIntArray(1);
	FATALIF(intArray == NULL, "Out of local resource of out of memory: intArray == NULL");

	env->ExceptionClear();
	jint result = env->CallIntMethod(javaImplementation, ReadMethodID, byteArray, intArray);
		
	if (env->ExceptionCheck())
	{
		env->DeleteLocalRef(byteArray);
		env->DeleteLocalRef(intArray);
		return S_FALSE;
	}
	
	if (result)
	{
		env->DeleteLocalRef(byteArray);
		env->DeleteLocalRef(intArray);
		return result;
	}
	
	jint * read = env->GetIntArrayElements(intArray, NULL);
	if (processedSize)
	{
		*processedSize = (UInt32)*read;
	}
	
	jbyte * buffer = env->GetByteArrayElements(byteArray, NULL);
	memcpy(data, buffer, size);
	env->ReleaseByteArrayElements(byteArray, buffer, JNI_ABORT);
	
	env->DeleteLocalRef(byteArray);
	env->DeleteLocalRef(intArray);
	env->ReleaseIntArrayElements(intArray, read, JNI_ABORT);

//	printf("Success read %i\n", *processedSize);
//	fflush(stdout);
//	
//	for (int i = 0; i < *processedSize; i++)
//	{
//		if (i%32==31)
//		{
//			printf("\n");
//		}
//		printf("%02X ", 0xFF & ((char*)data)[i]);
//	}
//	printf("\n");
//	fflush(stdout);
	
	return result;
}

