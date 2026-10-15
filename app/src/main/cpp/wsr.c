#include <jni.h>
#include <android/bitmap.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <freerdp/freerdp.h>
#include <freerdp/gdi/gdi.h>
#include <freerdp/input.h>
#include <freerdp/settings.h>
#include <freerdp/error.h>
#include <winpr/synch.h>

typedef struct { rdpContext c; } WsrCtx;

static JNIEnv *E;
static jobject self, bmp;
static jmethodID mLog, mErr, mOk, mFrame, mCert;
static freerdp *inst;
static rdpInput *inp;
static volatile int running, dirty;
static int mx, my, W, H;
static long last;
static BOOL (*orig)(rdpContext *);

static long now(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1000L + t.tv_nsec / 1000000L;
}

static void callStr(jmethodID m, const char *s) {
    jstring j = (*E)->NewStringUTF(E, s);
    (*E)->CallVoidMethod(E, self, m, j);
    (*E)->DeleteLocalRef(E, j);
}

static void push(void) {
    AndroidBitmapInfo bi;
    void *px;
    rdpGdi *g;
    if (!inst || !bmp) return;
    g = inst->context->gdi;
    if (!g || !g->primary_buffer) return;
    if (AndroidBitmap_getInfo(E, bmp, &bi) < 0) return;
    if (AndroidBitmap_lockPixels(E, bmp, &px) < 0) return;
    int w = g->width < (int)bi.width ? g->width : (int)bi.width;
    int h = g->height < (int)bi.height ? g->height : (int)bi.height;
    for (int y = 0; y < h; y++)
        memcpy((char *)px + (size_t)y * bi.stride, g->primary_buffer + (size_t)y * g->stride, (size_t)w * 4);
    AndroidBitmap_unlockPixels(E, bmp);
    dirty = 0;
    last = now();
    (*E)->CallVoidMethod(E, self, mFrame);
}

static BOOL endpaint(rdpContext *c) {
    if (orig) orig(c);
    dirty = 1;
    if (now() - last >= 50) push();
    return TRUE;
}

static BOOL pre(freerdp *i) {
    callStr(mLog, "TCP connected. Negotiating security (NLA/TLS)");
    return TRUE;
}

static BOOL post(freerdp *i) {
    callStr(mLog, "Authenticated. Initializing display");
    if (!gdi_init(i, PIXEL_FORMAT_RGBA32)) return FALSE;
    orig = i->context->update->EndPaint;
    i->context->update->EndPaint = endpaint;
    return TRUE;
}

static void postdc(freerdp *i) {
    gdi_free(i);
}

static DWORD verify(freerdp *i, const char *host, UINT16 port, const char *cn,
                    const char *subject, const char *issuer, const char *fp, DWORD flags) {
    char b[768];
    snprintf(b, sizeof b, "%s:%u\nCN: %s\n%s", host ? host : "", port, cn ? cn : "", fp ? fp : "");
    jstring j = (*E)->NewStringUTF(E, b);
    jboolean ok = (*E)->CallBooleanMethod(E, self, mCert, j);
    (*E)->DeleteLocalRef(E, j);
    return ok ? 2 : 0;
}

static void reportError(void) {
    char b[256];
    UINT32 err = freerdp_get_last_error(inst->context);
    if (err == 0)
        snprintf(b, sizeof b, "0x00000000 Disconnected by server");
    else
        snprintf(b, sizeof b, "0x%08X %s", err, freerdp_get_last_error_name(err));
    callStr(mErr, b);
}

JNIEXPORT void JNICALL Java_com_example_wsr_FreeRdpClient_nativeRun(
    JNIEnv *e, jobject t, jstring jh, jint port, jstring ju, jstring jp, jstring jg,
    jboolean adm, jint w, jint h, jobject b) {
    E = e;
    self = (*e)->NewGlobalRef(e, t);
    bmp = (*e)->NewGlobalRef(e, b);
    W = w;
    H = h;
    mx = w / 2;
    my = h / 2;
    jclass c = (*e)->GetObjectClass(e, t);
    mLog = (*e)->GetMethodID(e, c, "jLog", "(Ljava/lang/String;)V");
    mErr = (*e)->GetMethodID(e, c, "jError", "(Ljava/lang/String;)V");
    mOk = (*e)->GetMethodID(e, c, "jOk", "()V");
    mFrame = (*e)->GetMethodID(e, c, "jFrame", "()V");
    mCert = (*e)->GetMethodID(e, c, "jCert", "(Ljava/lang/String;)Z");

    inst = freerdp_new();
    inst->ContextSize = sizeof(WsrCtx);
    inst->PreConnect = pre;
    inst->PostConnect = post;
    inst->PostDisconnect = postdc;
    inst->VerifyCertificateEx = verify;
    if (!freerdp_context_new(inst)) {
        callStr(mErr, "0x00000002 Context creation failed");
        goto done;
    }
    rdpSettings *s = inst->context->settings;
    inp = inst->context->input;

    const char *host = (*e)->GetStringUTFChars(e, jh, 0);
    const char *user = (*e)->GetStringUTFChars(e, ju, 0);
    const char *pass = (*e)->GetStringUTFChars(e, jp, 0);
    const char *gw = (*e)->GetStringUTFChars(e, jg, 0);

    char uname[256];
    snprintf(uname, sizeof uname, "%s", user);
    char *bs = strchr(uname, '\\');
    if (bs) {
        *bs = 0;
        freerdp_settings_set_string(s, FreeRDP_Domain, uname);
        freerdp_settings_set_string(s, FreeRDP_Username, bs + 1);
    } else {
        freerdp_settings_set_string(s, FreeRDP_Username, uname);
    }
    freerdp_settings_set_string(s, FreeRDP_ServerHostname, host);
    freerdp_settings_set_uint32(s, FreeRDP_ServerPort, (UINT32)port);
    freerdp_settings_set_string(s, FreeRDP_Password, pass);
    freerdp_settings_set_uint32(s, FreeRDP_DesktopWidth, (UINT32)w);
    freerdp_settings_set_uint32(s, FreeRDP_DesktopHeight, (UINT32)h);
    freerdp_settings_set_uint32(s, FreeRDP_ColorDepth, 32);
    freerdp_settings_set_uint32(s, FreeRDP_TcpConnectTimeout, 300000);
    freerdp_settings_set_bool(s, FreeRDP_SoftwareGdi, TRUE);
    freerdp_settings_set_bool(s, FreeRDP_ConsoleSession, adm ? TRUE : FALSE);
    freerdp_settings_set_bool(s, FreeRDP_NlaSecurity, TRUE);
    freerdp_settings_set_bool(s, FreeRDP_TlsSecurity, TRUE);
    freerdp_settings_set_bool(s, FreeRDP_RdpSecurity, TRUE);
    freerdp_settings_set_bool(s, FreeRDP_AutoLogonEnabled, TRUE);
    freerdp_settings_set_bool(s, FreeRDP_SupportGraphicsPipeline, FALSE);
    freerdp_settings_set_bool(s, FreeRDP_IgnoreCertificate, FALSE);
    if (gw[0] != 0) {
        freerdp_settings_set_string(s, FreeRDP_GatewayHostname, gw);
        freerdp_settings_set_bool(s, FreeRDP_GatewayEnabled, TRUE);
        freerdp_settings_set_bool(s, FreeRDP_GatewayUseSameCredentials, TRUE);
    }
    (*e)->ReleaseStringUTFChars(e, jh, host);
    (*e)->ReleaseStringUTFChars(e, ju, user);
    (*e)->ReleaseStringUTFChars(e, jp, pass);
    (*e)->ReleaseStringUTFChars(e, jg, gw);

    running = 1;
    dirty = 0;
    last = 0;
    callStr(mLog, "Connecting (TCP)");
    if (!freerdp_connect(inst)) {
        if (running) reportError();
        goto done;
    }
    (*e)->CallVoidMethod(e, self, mOk);

    while (running && !freerdp_shall_disconnect_context(inst->context)) {
        HANDLE hs[64];
        DWORD n = freerdp_get_event_handles(inst->context, hs, 64);
        if (n == 0) break;
        DWORD r = WaitForMultipleObjects(n, hs, FALSE, 50);
        if (r == WAIT_FAILED) break;
        if (!freerdp_check_event_handles(inst->context)) break;
        if (dirty && now() - last >= 50) push();
    }
    if (running) reportError();

done:
    running = 0;
    inp = NULL;
    if (inst) {
        freerdp_disconnect(inst);
        freerdp_context_free(inst);
        freerdp_free(inst);
        inst = NULL;
    }
    (*e)->DeleteGlobalRef(e, self);
    (*e)->DeleteGlobalRef(e, bmp);
    bmp = NULL;
}

JNIEXPORT void JNICALL Java_com_example_wsr_FreeRdpClient_nativeStop(JNIEnv *e, jobject t) {
    running = 0;
    if (inst) freerdp_abort_connect_context(inst->context);
}

JNIEXPORT void JNICALL Java_com_example_wsr_FreeRdpClient_nativeMove(JNIEnv *e, jobject t, jint dx, jint dy) {
    if (!inp) return;
    mx += dx;
    my += dy;
    if (mx < 0) mx = 0;
    if (my < 0) my = 0;
    if (mx > W - 1) mx = W - 1;
    if (my > H - 1) my = H - 1;
    freerdp_input_send_mouse_event(inp, PTR_FLAGS_MOVE, (UINT16)mx, (UINT16)my);
}

JNIEXPORT void JNICALL Java_com_example_wsr_FreeRdpClient_nativeButton(JNIEnv *e, jobject t, jint btn, jboolean down) {
    if (!inp) return;
    UINT16 f = (btn == 0 ? PTR_FLAGS_BUTTON1 : PTR_FLAGS_BUTTON2);
    if (down) f |= PTR_FLAGS_DOWN;
    freerdp_input_send_mouse_event(inp, f, (UINT16)mx, (UINT16)my);
}

JNIEXPORT void JNICALL Java_com_example_wsr_FreeRdpClient_nativeWheel(JNIEnv *e, jobject t, jboolean up) {
    if (!inp) return;
    UINT16 f = up ? (PTR_FLAGS_WHEEL | 0x78) : (PTR_FLAGS_WHEEL | PTR_FLAGS_WHEEL_NEGATIVE | 0x88);
    freerdp_input_send_mouse_event(inp, f, (UINT16)mx, (UINT16)my);
}

JNIEXPORT void JNICALL Java_com_example_wsr_FreeRdpClient_nativeKey(JNIEnv *e, jobject t, jint sc, jboolean down) {
    if (!inp) return;
    freerdp_input_send_keyboard_event_ex(inp, down ? TRUE : FALSE, FALSE, (UINT32)sc);
}

JNIEXPORT void JNICALL Java_com_example_wsr_FreeRdpClient_nativeUnicode(JNIEnv *e, jobject t, jint code, jboolean down) {
    if (!inp) return;
    freerdp_input_send_unicode_keyboard_event(inp, down ? KBD_FLAGS_DOWN : KBD_FLAGS_RELEASE, (UINT16)code);
}
