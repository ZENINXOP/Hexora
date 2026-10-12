// Owned native APK-read acceptance fixture. AI-assisted contribution: OpenAI Codex.
#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <zlib.h>

extern "C" JNIEXPORT jlong JNICALL
Java_app_hexora_signaturefixture_MainActivity_nativeApkCrc(JNIEnv* env, jclass, jstring path) {
    if (path == nullptr) return -1;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return -1;
    int fd = open(chars, O_RDONLY | O_CLOEXEC);
    env->ReleaseStringUTFChars(path, chars);
    if (fd < 0) return -1;
    unsigned char buffer[16384];
    uLong crc = crc32(0L, Z_NULL, 0);
    ssize_t count;
    while ((count = read(fd, buffer, sizeof(buffer))) > 0) {
        crc = crc32(crc, buffer, static_cast<uInt>(count));
    }
    close(fd);
    return count < 0 ? -1 : static_cast<jlong>(crc);
}
