#include <string.h>
#include <jni.h>
#include "util.h"
#include "base64.h"
#include <openssl/evp.h>

#define KEY_PREFIX "A8P20vWlvfSu3JMO6tBjgr05UvjHAh2x"
#define LOG_TAG "JNI_LOG_TAG"
#define ERROR "CRYPTO_ERROR"

const EVP_CIPHER *cipher;
const EVP_MD *dgst = NULL;
const unsigned char *salt = NULL;

jstring
Java_com_igexin_log_restapi_util_CryptoTool_encrypt(JNIEnv *env,
                                           jobject obj,
                                           jstring pkg_name,
                                           jstring content) {
    const char *pkg_char = (*env)->GetStringUTFChars(env, pkg_name, NULL);
    char *key_contact = str_contact(pkg_char, KEY_PREFIX);
    char *key_char = base64_encode(key_contact, strlen(key_contact));

    const char *input = (*env)->GetStringUTFChars(env, content, NULL);

    unsigned char key[EVP_MAX_KEY_LENGTH];
    unsigned char iv[EVP_MAX_IV_LENGTH];

    OpenSSL_add_all_algorithms();

    cipher = EVP_get_cipherbyname("aes-256-cbc");
    dgst = EVP_get_digestbyname("md5");

    if (!cipher) {
        return (*env)->NewStringUTF(env, ERROR);
    }

    if (!dgst) {
        return (*env)->NewStringUTF(env, ERROR);
    }

    if (!EVP_BytesToKey(cipher, dgst, salt,
                        (unsigned char *) key_char,
                        strlen(key_char), 1, key, iv)) {
        return (*env)->NewStringUTF(env, ERROR);
    }

    int input_len;
    unsigned char *cipher_text;

    EVP_CIPHER_CTX ctx;
    EVP_CIPHER_CTX_init(&ctx);

    if (!EVP_EncryptInit_ex(&ctx, EVP_aes_256_cbc(), NULL, key, iv)) {
        return (*env)->NewStringUTF(env, ERROR);
    };

    input_len = strlen(input) + 1;
    cipher_text = (unsigned char *) malloc(input_len + EVP_CIPHER_CTX_block_size(&ctx));

    int bytes_written = 0;
    int ciphertext_len = 0;
    if (!EVP_EncryptUpdate(&ctx, cipher_text, &bytes_written, input, input_len)) {
        return (*env)->NewStringUTF(env, ERROR);
    };
    ciphertext_len += bytes_written;

    if (!EVP_EncryptFinal_ex(&ctx, cipher_text + bytes_written, &bytes_written)) {
        return (*env)->NewStringUTF(env, ERROR);
    };
    ciphertext_len += bytes_written;

    EVP_CIPHER_CTX_cleanup(&ctx);

    char *cipher_text_base64 = base64_encode(cipher_text, ciphertext_len);

    char len[ciphertext_len + 2];
    sprintf(len, "%d__", ciphertext_len);
    char *len_str = str_contact(len, cipher_text_base64);

    char *result = base64_encode(len_str, strlen(len_str));

    (*env)->ReleaseStringUTFChars(env, pkg_name, pkg_char);
    (*env)->ReleaseStringUTFChars(env, content, input);

    free(cipher_text);
    free(key_contact);
    free(key_char);

    return (*env)->NewStringUTF(env, result);
}

jstring
Java_com_igexin_log_restapi_util_CryptoTool_decrypt(JNIEnv *env,
                                           jobject obj,
                                           jstring pkg_name,
                                           jstring content) {
    const char *pkg_char = (*env)->GetStringUTFChars(env, pkg_name, NULL);
    char *key_contact = str_contact(pkg_char, KEY_PREFIX);
    char *key_char = base64_encode(key_contact, strlen(key_contact));

    const char *cipher_text = (*env)->GetStringUTFChars(env, content, NULL);
    char *input = base64_decode(cipher_text, strlen(cipher_text));

    char str[strlen(input)];
    char cipher_len[16];
    sscanf(input, "%[0-9]__%[^.]", cipher_len, str);
    int ciphertext_len = atoi(cipher_len);

    char *de_cipher_text = base64_decode(str, strlen(str));

    unsigned char key[EVP_MAX_KEY_LENGTH];
    unsigned char iv[EVP_MAX_IV_LENGTH];

    OpenSSL_add_all_algorithms();

    cipher = EVP_get_cipherbyname("aes-256-cbc");
    dgst = EVP_get_digestbyname("md5");

    if (!cipher) {
        return (*env)->NewStringUTF(env, ERROR);
    }

    if (!dgst) {
        return (*env)->NewStringUTF(env, ERROR);
    }

    if (!EVP_BytesToKey(cipher, dgst, salt,
                        (unsigned char *) key_char,
                        strlen(key_char), 1, key, iv)) {
        return (*env)->NewStringUTF(env, ERROR);
    }

    unsigned char *plain_text;
    EVP_CIPHER_CTX ctx;
    EVP_CIPHER_CTX_init(&ctx);

    if (!EVP_DecryptInit_ex(&ctx, EVP_aes_256_cbc(), NULL, key, iv)) {
        return (*env)->NewStringUTF(env, ERROR);
    };
    plain_text = malloc(ciphertext_len);

    int de_bytes_written = 0;
    int de_plaintext_len = 0;
    if (!EVP_DecryptUpdate(&ctx, plain_text, &de_bytes_written, de_cipher_text, ciphertext_len)) {
        return (*env)->NewStringUTF(env, ERROR);
    };
    de_plaintext_len += de_bytes_written;

    if (!EVP_DecryptFinal_ex(&ctx, plain_text + de_bytes_written, &de_bytes_written)) {
        return (*env)->NewStringUTF(env, ERROR);
    };
    de_plaintext_len += de_bytes_written;

    EVP_CIPHER_CTX_cleanup(&ctx);

    (*env)->ReleaseStringUTFChars(env, pkg_name, pkg_char);
    (*env)->ReleaseStringUTFChars(env, content, cipher_text);
    free(key_contact);
    free(key_char);
    free(input);
    free(de_cipher_text);

    return (*env)->NewStringUTF(env, plain_text);
}