#include <jni.h>
#include <android/bitmap.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <sys/stat.h>
#include <errno.h>
#include <freerdp/freerdp.h>
#include <freerdp/addin.h>
#include <freerdp/gdi/gdi.h>
#include <freerdp/input.h>
#include <freerdp/settings.h>
#include <freerdp/error.h>
#include <freerdp/client/channels.h>
#include <freerdp/client/cliprdr.h>
#include <freerdp/channels/cliprdr.h>
#include <freerdp/channels/rdpdr.h>
#include <winpr/synch.h>
#include <winpr/wlog.h>
#include <winpr/string.h>

typedef struct {
    rdpContext c;
    char pad[16384];
} WsrCtx;

static JNIEnv *E;
static jobject self, bmp;
static jmethodID mLog, mErr, mOk, mFrame, mCert, mClip, mRes;
static freerdp *inst;
static rdpInput *inp;
static CliprdrClientContext *clip;
static volatile int running, dirty;
static int mx, my, W, H, gClip = 1, provReg = 0;
static long last;
static BOOL (*orig)(rdpContext *);
static char lastLog[512];
static WCHAR *phoneClip;
static size_t phoneClipBytes;

static BOOL logMsg(const wLogMessage *m) {
    if (m && m->TextString) snprintf(lastLog, sizeof lastLog, "%s", m->TextString);
    return TRUE;
}

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

static void addStatic(rdpSettings *s, const char *a, const char *b) {
    const char *argv[2] = { a, b };
    ADDIN_ARGV *ch;
    if (freerdp_static_channel_collection_find(s, a)) return;
    ch = freerdp_addin_argv_new(b ? 2 : 1, argv);
    if (ch) freerdp_static_channel_collection_add(s, ch);
}

static void addDynamic(rdpSettings *s, const char *a, const char *b) {
    const char *argv[2] = { a, b };
    ADDIN_ARGV *ch = freerdp_addin_argv_new(b ? 2 : 1, argv);
    if (ch) freerdp_dynamic_channel_collection_add(s, ch);
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

static UINT cbCaps(CliprdrClientContext *c, const CLIPRDR_CAPABILITIES *caps) {
    return CHANNEL_RC_OK;
}

static UINT sendFormatList(CliprdrClientContext *c) {
    CLIPRDR_FORMAT f;
    CLIPRDR_FORMAT_LIST l;
    memset(&f, 0, sizeof f);
    memset(&l, 0, sizeof l);
    f.formatId = CF_UNICODETEXT;
    f.formatName = NULL;
    l.common.msgType = CB_FORMAT_LIST;
    l.numFormats = 1;
    l.formats = &f;
    return c->ClientFormatList(c, &l);
}

static UINT cbMonitorReady(CliprdrClientContext *c, const CLIPRDR_MONITOR_READY *m) {
    CLIPRDR_CAPABILITIES caps;
    CLIPRDR_GENERAL_CAPABILITY_SET gen;
    memset(&caps, 0, sizeof caps);
    memset(&gen, 0, sizeof gen);
    caps.cCapabilitiesSets = 1;
    caps.capabilitySets = (CLIPRDR_CAPABILITY_SET *)&gen;
    gen.capabilitySetType = CB_CAPSTYPE_GENERAL;
    gen.capabilitySetLength = 12;
    gen.version = CB_CAPS_VERSION_2;
    gen.generalFlags = CB_USE_LONG_FORMAT_NAMES;
    UINT r = c->ClientCapabilities(c, &caps);
    if (r != CHANNEL_RC_OK) return r;
    return sendFormatList(c);
}

static UINT cbFormatList(CliprdrClientContext *c, const CLIPRDR_FORMAT_LIST *l) {
    CLIPRDR_FORMAT_LIST_RESPONSE resp;
    memset(&resp, 0, sizeof resp);
    resp.common.msgType = CB_FORMAT_LIST_RESPONSE;
    resp.common.msgFlags = CB_RESPONSE_OK;
    UINT r = c->ClientFormatListResponse(c, &resp);
    if (r != CHANNEL_RC_OK) return r;
    int has = 0;
    for (UINT32 i = 0; i < l->numFormats; i++)
        if (l->formats[i].formatId == CF_UNICODETEXT) has = 1;
    if (!has) return CHANNEL_RC_OK;
    CLIPRDR_FORMAT_DATA_REQUEST req;
    memset(&req, 0, sizeof req);
    req.common.msgType = CB_FORMAT_DATA_REQUEST;
    req.requestedFormatId = CF_UNICODETEXT;
    return c->ClientFormatDataRequest(c, &req);
}

static UINT cbFormatListResponse(CliprdrClientContext *c, const CLIPRDR_FORMAT_LIST_RESPONSE *r) {
    return CHANNEL_RC_OK;
}

static UINT cbDataRequest(CliprdrClientContext *c, const CLIPRDR_FORMAT_DATA_REQUEST *req) {
    CLIPRDR_FORMAT_DATA_RESPONSE resp;
    memset(&resp, 0, sizeof resp);
    resp.common.msgType = CB_FORMAT_DATA_RESPONSE;
    if (req->requestedFormatId == CF_UNICODETEXT && phoneClip) {
        resp.common.msgFlags = CB_RESPONSE_OK;
        resp.common.dataLen = (UINT32)phoneClipBytes;
        resp.requestedFormatData = (const BYTE *)phoneClip;
    } else {
        resp.common.msgFlags = CB_RESPONSE_FAIL;
    }
    return c->ClientFormatDataResponse(c, &resp);
}

static UINT cbDataResponse(CliprdrClientContext *c, const CLIPRDR_FORMAT_DATA_RESPONSE *r) {
    if (!(r->common.msgFlags & CB_RESPONSE_OK) || !r->requestedFormatData) return CHANNEL_RC_OK;
    size_t n = r->common.dataLen / sizeof(WCHAR);
    size_t outLen = 0;
    char *u8 = ConvertWCharNToUtf8Alloc((const WCHAR *)r->requestedFormatData, n, &outLen);
    if (u8) {
        jstring j = (*E)->NewStringUTF(E, u8);
        (*E)->CallVoidMethod(E, self, mClip, j);
        (*E)->DeleteLocalRef(E, j);
        free(u8);
    }
    return CHANNEL_RC_OK;
}

static void onChan(void *ctx, const ChannelConnectedEventArgs *e) {
    if (strcmp(e->name, CLIPRDR_SVC_CHANNEL_NAME) == 0) {
        clip = (CliprdrClientContext *)e->pInterface;
        clip->ServerCapabilities = cbCaps;
        clip->MonitorReady = cbMonitorReady;
        clip->ServerFormatList = cbFormatList;
        clip->ServerFormatListResponse = cbFormatListResponse;
        clip->ServerFormatDataRequest = cbDataRequest;
        clip->ServerFormatDataResponse = cbDataResponse;
    }
}

static void onChanDown(void *ctx, const ChannelDisconnectedEventArgs *e) {
    if (strcmp(e->name, CLIPRDR_SVC_CHANNEL_NAME) == 0) clip = NULL;
}

static BOOL ctxNew(freerdp *i, rdpContext *c) { return TRUE; }
static void ctxFree(freerdp *i, rdpContext *c) {}

static BOOL pre(freerdp *i) {
    callStr(mLog, "TCP connected. Negotiating security (NLA/TLS)");
    rdpSettings *s = i->context->settings;
    freerdp_settings_set_bool(s, FreeRDP_RedirectClipboard, gClip ? TRUE : FALSE);
    if (!freerdp_client_load_addins(i->context->channels, s)) {
        char m[640];
        snprintf(m, sizeof m, "Channels could not be loaded: %s", lastLog);
        callStr(mLog, m);
    }
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
    char b[900];
    UINT32 err = freerdp_get_last_error(inst->context);
    if (err == 0)
        snprintf(b, sizeof b, "0x00000000 Disconnected by server | %s", lastLog);
    else
        snprintf(b, sizeof b, "0x%08X %s | %s", err, freerdp_get_last_error_name(err), lastLog);
    callStr(mErr, b);
}

JNIEXPORT void JNICALL Java_com_example_wsr_FreeRdpClient_nativeRun(
    JNIEnv *e, jobject t, jstring jh, jint port, jstring ju, jstring jp, jstring jg,
    jboolean adm, jint audio, jboolean mic, jstring jst, jboolean clipOn,
    jint w, jint h, jobject b) {
    E = e;
    self = (*e)->NewGlobalRef(e, t);
    bmp = (*e)->NewGlobalRef(e, b);
    W = w;
    H = h;
    mx = w / 2;
    my = h / 2;
    clip = NULL;
    gClip = clipOn ? 1 : 0;
    lastLog[0] = 0;
    setenv("HOME", "/data/data/com.example.wsr/files", 1);
    setenv("XDG_CONFIG_HOME", "/data/data/com.example.wsr/files/.config", 1);
    setenv("TMPDIR", "/data/data/com.example.wsr/cache", 1);
    mkdir("/data/data/com.example.wsr/files", 0700);
    mkdir("/data/data/com.example.wsr/files/.config", 0700);
    mkdir("/data/data/com.example.wsr/cache", 0700);
    jclass c = (*e)->GetObjectClass(e, t);
    mLog = (*e)->GetMethodID(e, c, "jLog", "(Ljava/lang/String;)V");
    mErr = (*e)->GetMethodID(e, c, "jError", "(Ljava/lang/String;)V");
    mOk = (*e)->GetMethodID(e, c, "jOk", "()V");
    mFrame = (*e)->GetMethodID(e, c, "jFrame", "()V");
    mCert = (*e)->GetMethodID(e, c, "jCert", "(Ljava/lang/String;)Z");
    mClip = (*e)->GetMethodID(e, c, "jClip", "(Ljava/lang/String;)V");
    mRes = (*e)->GetMethodID(e, c, "jRes", "(II)V");

    if (!provReg) {
        freerdp_register_addin_provider(freerdp_channels_load_static_addin_entry, 0);
        provReg = 1;
    }

    wLog *root = WLog_GetRoot();
    WLog_SetLogAppenderType(root, WLOG_APPENDER_CALLBACK);
    wLogAppender *ap = WLog_GetLogAppender(root);
    wLogCallbacks cb;
    memset(&cb, 0, sizeof cb);
    cb.message = logMsg;
    WLog_ConfigureAppender(ap, "callbacks", &cb);
    WLog_SetLogLevel(root, WLOG_ERROR);

    inst = freerdp_new();
    if (!inst) {
        callStr(mErr, "0x00000003 freerdp_new failed");
        goto done;
    }
    inst->ContextSize = sizeof(WsrCtx);
    inst->ContextNew = ctxNew;
    inst->ContextFree = ctxFree;
    inst->PreConnect = pre;
    inst->PostConnect = post;
    inst->PostDisconnect = postdc;
    inst->VerifyCertificateEx = verify;
    errno = 0;
    if (!freerdp_context_new(inst)) {
        char eb[900];
        snprintf(eb, sizeof eb, "0x00000002 Context creation failed | errno=%d %s | %s", errno, strerror(errno), lastLog);
        callStr(mErr, eb);
        freerdp_free(inst);
        inst = NULL;
        goto done;
    }
    rdpSettings *s = inst->context->settings;
    if (!s) {
        callStr(mErr, "0x00000004 Settings not created");
        goto done;
    }
    inp = inst->context->input;
    PubSub_SubscribeChannelConnected(inst->context->pubSub, onChan);
    PubSub_SubscribeChannelDisconnected(inst->context->pubSub, onChanDown);

    const char *host = (*e)->GetStringUTFChars(e, jh, 0);
    const char *user = (*e)->GetStringUTFChars(e, ju, 0);
    const char *pass = (*e)->GetStringUTFChars(e, jp, 0);
    const char *gw = (*e)->GetStringUTFChars(e, jg, 0);
    const char *st = (*e)->GetStringUTFChars(e, jst, 0);

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
    freerdp_settings_set_bool(s, FreeRDP_RedirectClipboard, gClip ? TRUE : FALSE);

    freerdp_settings_set_bool(s, FreeRDP_AudioPlayback, audio == 0 ? TRUE : FALSE);
    freerdp_settings_set_bool(s, FreeRDP_RemoteConsoleAudio, audio == 1 ? TRUE : FALSE);
    freerdp_settings_set_bool(s, FreeRDP_AudioCapture, mic ? TRUE : FALSE);
    if (audio == 0) addStatic(s, "rdpsnd", "sys:opensles");
    if (mic) {
        freerdp_settings_set_bool(s, FreeRDP_SupportDynamicChannels, TRUE);
        addStatic(s, "drdynvc", NULL);
        addDynamic(s, "audin", "sys:opensles");
    }
    if (st[0] != 0) {
        freerdp_settings_set_bool(s, FreeRDP_DeviceRedirection, TRUE);
        freerdp_settings_set_bool(s, FreeRDP_RedirectDrives, TRUE);
        addStatic(s, "rdpdr", NULL);
        const char *dv[2] = { "Phone", st };
        RDPDR_DEVICE *dev = freerdp_device_new(RDPDR_DTYP_FILESYSTEM, 2, dv);
        if (dev) freerdp_device_collection_add(s, dev);
    }

    if (gw[0] != 0) {
        freerdp_settings_set_string(s, FreeRDP_GatewayHostname, gw);
        freerdp_settings_set_bool(s, FreeRDP_GatewayEnabled, TRUE);
        freerdp_settings_set_bool(s, FreeRDP_GatewayUseSameCredentials, TRUE);
    }
    (*e)->ReleaseStringUTFChars(e, jh, host);
    (*e)->ReleaseStringUTFChars(e, ju, user);
    (*e)->ReleaseStringUTFChars(e, jp, pass);
    (*e)->ReleaseStringUTFChars(e, jg, gw);
    (*e)->ReleaseStringUTFChars(e, jst, st);

    running = 1;
    dirty = 0;
    last = 0;
    callStr(mLog, "Connecting (TCP)");
    if (!freerdp_connect(inst)) {
        if (running) reportError();
        goto done;
    }
    {
        UINT32 grW = freerdp_settings_get_uint32(s, FreeRDP_DesktopWidth);
        UINT32 grH = freerdp_settings_get_uint32(s, FreeRDP_DesktopHeight);
        if (grW > 0 && grH > 0) {
            W = (int)grW;
            H = (int)grH;
            mx = W / 2;
            my = H / 2;
        }
        (*e)->CallVoidMethod(e, self, mRes, (jint)W, (jint)H);
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
    clip = NULL;
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

JNIEXPORT void JNICALL Java_com_example_wsr_FreeRdpClient_nativeSetClip(JNIEnv *e, jobject t, jstring js) {
    const char *u = (*e)->GetStringUTFChars(e, js, 0);
    size_t n = 0;
    WCHAR *w = ConvertUtf8ToWCharAlloc(u, &n);
    (*e)->ReleaseStringUTFChars(e, js, u);
    if (!w) return;
    WCHAR *old = phoneClip;
    phoneClipBytes = (n + 1) * sizeof(WCHAR);
    phoneClip = w;
    free(old);
    if (clip) sendFormatList(clip);
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
