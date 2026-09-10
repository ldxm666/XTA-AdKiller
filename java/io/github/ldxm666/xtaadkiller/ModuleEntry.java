package io.github.ldxm666.xtaadkiller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public final class ModuleEntry extends XposedModule {

    private static final String TAG = "XTAK";
    private static final String TARGET_PKG = "com.xtuone.android.syllabus";

    private static final String[] AMPS_API_CLASSES = {
            "xyz.adscope.amps.ad.splash.AMPSSplashAd",
            "xyz.adscope.amps.ad.banner.AMPSBannerAd",
            "xyz.adscope.amps.ad.draw.AMPSDrawAd",
            "xyz.adscope.amps.ad.interstitial.AMPSInterstitialAd",
            "xyz.adscope.amps.ad.nativead.AMPSNativeAd",
            "xyz.adscope.amps.ad.reward.AMPSRewardVideoAd",
            "xyz.adscope.amps.ad.unified.AMPSUnifiedNativeAd",
    };
    private static final String[] SPLASH_ADAPTERS = {
            "com.xtuone.android.friday.advertising.adapter.HCSplashAdapter",
            "com.xtuone.android.friday.advertising.adapter.YTSplashAdapter",
            "com.xtuone.android.friday.advertising.adapter.JZSplashAdapter",
            "com.xtuone.android.friday.advertising.adapter.MerakCustomSplashAdapter",
    };
    private static final String AMPSAdapterModel = "xyz.adscope.amps.model.AMPSAdapterModel";
    private static final String AMPSSplashAdAdapterListener =
            "xyz.adscope.amps.ad.splash.adapter.AMPSSplashAdAdapterListener";
    private static final String AMPSBaseAdAdapterListener =
            "xyz.adscope.amps.inner.AMPSBaseAdAdapterListener";
    private static final String WALL_MANAGER =
            "com.xtuone.android.friday.advertising.advertisementwall.AdvertisementWallManager";
    private static final String AD_CONTAINER =
            "com.xtuone.android.friday.widget.AdContainer";
    private static final String BANNER_ITEM =
            "com.xtuone.android.friday.advertising.advertisementwall.b";
    private static final String TABLE_SCREEN =
            "com.xtuone.android.friday.advertising.TableScreenAdManager";
    private static final String TABLE_AD_TYPE =
            "com.xtuone.android.friday.advertising.TableScreenAdManager$AdType";
    private static final String HOME_PREFETCH =
            "com.xtuone.android.friday.advertising.HomeAdPrefetchManager";
    private static final String ADVERTISING_BO =
            "com.xtuone.android.friday.bo.advertising.AdvertisingBO";
    private static final String BEIZI_INTERSTITIAL =
            "com.beizi.fusion.InterstitialAd";

    private volatile boolean started = false;
    private final Set<String> done = new HashSet<>();
    private int remaining;

    private final ConcurrentHashMap<Object, WeakReference<Object>> listeners =
            new ConcurrentHashMap<>();
    private volatile Constructor<?> ampsErrorCtor;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "event=module_loaded v=62 process=" + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!TARGET_PKG.equals(param.getPackageName())) {
            return;
        }
        log(Log.INFO, TAG, "event=package_loaded");
        start(param.getDefaultClassLoader());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET_PKG.equals(param.getPackageName())) {
            return;
        }
        log(Log.INFO, TAG, "event=package_ready");
        start(param.getClassLoader());
    }

    private synchronized void start(ClassLoader appCl) {
        if (started) {
            return;
        }
        started = true;
        remaining = AMPS_API_CLASSES.length + SPLASH_ADAPTERS.length + 4;
        flog("=== module start v64 ===");
        Thread t = new Thread(() -> poll(appCl), "xtak-kill");
        t.setDaemon(true);
        t.start();
    }

    private void poll(ClassLoader appCl) {
        // resolve AMPSError ctor early
        for (int i = 0; i < 200 && ampsErrorCtor == null; i++) {
            try {
                Class<?> err = appCl.loadClass("xyz.adscope.amps.common.AMPSError");
                ampsErrorCtor = err.getDeclaredConstructor(String.class, String.class);
                ampsErrorCtor.setAccessible(true);
            } catch (Throwable ignored) {
                sleepQuiet(150);
            }
        }
        for (int i = 0; i < 2400 && remaining > 0; i++) {
            try {
                for (String name : AMPS_API_CLASSES) {
                    if (!done.contains(name)) {
                        tryInstallAmpsApi(appCl, name);
                    }
                }
                for (String name : SPLASH_ADAPTERS) {
                    if (!done.contains(name)) {
                        tryInstallSplash(appCl, name);
                    }
                }
                if (!done.contains(WALL_MANAGER)) {
                    tryInstallWall(appCl, WALL_MANAGER);
                }
                if (!done.contains(BANNER_ITEM)) {
                    tryInstallBannerItem(appCl);
                }
                if (!done.contains(TABLE_SCREEN)) {
                    tryInstallTableScreen(appCl);
                }
                if (!done.contains("glide")) {
                    tryInstallGlide(appCl);
                }
                if (!done.contains("fresco")) {
                    tryInstallFresco(appCl);
                }
                if (!done.contains("volley")) {
                    tryInstallVolley(appCl);
                }
                if (!done.contains("adspace")) {
                    tryInstallAdSpaceKill(appCl);
                }
                if (!done.contains("courseskin")) {
                    tryInstallCourseSkin(appCl);
                }
                if (!done.contains("walltrace")) {
                    tryInstallWallTrace(appCl);
                }
            } catch (Throwable ignored) {
                // never kill the host
            }
            if (remaining > 0) {
                sleepQuiet(120);
            }
        }
        log(Log.INFO, TAG, "event=poll_exit remaining=" + remaining);
    }

    private static void flog(String msg) {
        try {
            java.io.File f = new java.io.File(
                    "/sdcard/Android/data/com.xtuone.android.syllabus/files/xtak_log.txt");
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.append(new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                    .format(new java.util.Date()))
                    .append(' ').append(msg).append('\n');
            w.close();
        } catch (Throwable ignored) {
        }
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    // ---- hookers -----------------------------------------------------------

    private final XposedInterface.Hooker blockWall = chain -> {
        log(Log.INFO, TAG, "KILL wall_attach");
        return null;
    };

    private final XposedInterface.Hooker blockSplashAdapter = chain -> {
        log(Log.INFO, TAG, "KILL splash_adapter "
                + chain.getExecutable().getName());
        return null;
    };

    /**
     * ctor hooker: remember the listener (last arg) per ad object.
     */
    private final XposedInterface.Hooker captureListener = chain -> {
        try {
            Object self = chain.getThisObject();
            Object[] args = chain.getArgs().toArray();
            if (self != null && args.length >= 3 && args[2] != null) {
                listeners.put(self, new WeakReference<>(args[2]));
            }
        } catch (Throwable ignored) {
        }
        return chain.proceed();
    };


    private final XposedInterface.Hooker loadAdBlocked = chain -> {
        try {
            Object self = chain.getThisObject();
            WeakReference<Object> ref = self == null ? null : listeners.get(self);
            final Object listener = ref == null ? null : ref.get();
            final Constructor<?> errCtor = ampsErrorCtor;
            final String cls = self == null ? "?" : self.getClass().getSimpleName();
            if (listener != null) {
                main.post(() -> fireFailure(listener, errCtor, cls));
            } else {
                log(Log.WARN, TAG, "loadAd blocked but no listener captured cls=" + cls);
            }
        } catch (Throwable t) {
            android.util.Log.e("XTAK2", "loadAd hook error: " + t);
        }
        log(Log.INFO, TAG, "KILL loadAd");
        return null;
    };

    private void fireFailure(Object listener, Constructor<?> errCtor, String cls) {
        try {
            Object err = null;
            if (errCtor != null) {
                try {
                    err = errCtor.newInstance("xtak", "ad blocked by module");
                } catch (Throwable ignored) {
                }
            }
            for (Method m : listener.getClass().getMethods()) {
                String n = m.getName().toLowerCase();
                if ((n.contains("fail") || n.contains("error")) && m.getParameterCount() <= 1) {
                    Class<?>[] ps = m.getParameterTypes();
                    m.setAccessible(true);
                    if (ps.length == 1 && err != null && ps[0].isInstance(err)) {
                        m.invoke(listener, err);
                    } else if (ps.length == 0) {
                        m.invoke(listener);
                    } else if (ps.length == 1) {
                        m.invoke(listener, (Object) null);
                    } else {
                        Object[] nulls = new Object[ps.length];
                        m.invoke(listener, nulls);
                    }
                    log(Log.INFO, TAG, "FIRED failure " + cls + " -> "
                            + listener.getClass().getSimpleName() + "#" + m.getName());
                    return;
                }
            }
            log(Log.WARN, TAG, "no failure method on " + listener.getClass().getName());
        } catch (Throwable t) {
            android.util.Log.e("XTAK2", "fireFailure error: " + t);
        }
    }

    // ---- installers --------------------------------------------------------

    private void tryInstallAmpsApi(ClassLoader cl, String name) {
        try {
            Class<?> c = cl.loadClass(name);
            int hooks = 0;
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                Class<?>[] ps = k.getParameterTypes();
                if (ps.length == 3 && ps[2].getName().contains("Listener")) {
                    k.setAccessible(true);
                    hook(k).setId("xtak_ctor_" + name.hashCode() + "_" + k.getParameterTypes().length)
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(captureListener);
                    hooks++;
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                String mn = m.getName();
                if (mn.equals("loadAd") || mn.equals("preLoad") || mn.equals("loadAdOnly")) {
                    m.setAccessible(true);
                    hook(m).setId("xtak_load_" + name.hashCode() + "_" + mn)
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(loadAdBlocked);
                    hooks++;
                } else if (mn.equals("show")) {
                    m.setAccessible(true);
                    hook(m).setId("xtak_show_" + name.hashCode())
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(blockWall);
                    hooks++;
                }
            }
            done.add(name);
            remaining--;
            flog("amps api hooked cls=" + name + " hooks=" + hooks);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError)) {
                flog("amps defer " + name + ": " + t);
            }
        }
    }

    private void tryInstallSplash(ClassLoader cl, String adapterName) {
        try {
            Class<?> adapter = cl.loadClass(adapterName);
            Class<?> model = cl.loadClass(AMPSAdapterModel);
            Class<?> lstA = cl.loadClass(AMPSSplashAdAdapterListener);
            Class<?> lstB = cl.loadClass(AMPSBaseAdAdapterListener);
            int installed = 0;
            Class<?>[][] overloads = {
                    {android.content.Context.class, model, lstA},
                    {android.content.Context.class, model, lstB},
            };
            for (Class<?>[] sig : overloads) {
                try {
                    Method m = adapter.getDeclaredMethod("loadNetworkAd", sig);
                    m.setAccessible(true);
                    hook(m).setId("xtak_" + adapterName.hashCode() + "_lna" + sig[2].getSimpleName())
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(blockSplashAdapter);
                    installed++;
                } catch (NoSuchMethodException ignored) {
                }
            }
            try {
                Method m = adapter.getDeclaredMethod("showAd", android.view.ViewGroup.class);
                m.setAccessible(true);
                hook(m).setId("xtak_" + adapterName.hashCode() + "_show")
                        .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                        .intercept(blockSplashAdapter);
                installed++;
            } catch (NoSuchMethodException ignored) {
            }
            done.add(adapterName);
            remaining--;
            log(Log.INFO, TAG, "event=splash_hooks_installed cls=" + adapterName + " hooks=" + installed);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError)) {
                android.util.Log.e("XTAK2", "splash defer " + adapterName + ": " + t);
            }
        }
    }

    private void tryInstallWall(ClassLoader cl, String wallName) {
        try {
            Class<?> wall = cl.loadClass(wallName);
            Class<?> container = cl.loadClass(AD_CONTAINER);
            int installed = 0;
            for (String mn : new String[]{"default", "throws"}) {
                try {
                    Method m = wall.getDeclaredMethod(mn,
                            android.app.Activity.class, container, boolean.class);
                    m.setAccessible(true);
                    hook(m).setId("xtak_wall_" + mn)
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(blockWall);
                    installed++;
                } catch (NoSuchMethodException ignored) {
                }
            }
            done.add(wallName);
            remaining--;
            flog("wall hooks installed=" + installed);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError)) {
                android.util.Log.e("XTAK2", "wall defer: " + t);
            }
        }
    }

    // ---- in-app ad surfaces ------------------------------------------------

    /** home banner item view: starve it of ad data and bitmaps */
    private void tryInstallBannerItem(ClassLoader cl) {
        try {
            Class<?> item = cl.loadClass(BANNER_ITEM);
            Class<?> bo = cl.loadClass(ADVERTISING_BO);
            int installed = 0;
            for (Method m : item.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                // static forms carry the receiver as ps[0]
                boolean boSet = ps.length == 2 && ps[0] == item && ps[1] == bo;
                boolean bmpSet = ps.length == 2 && ps[0] == item && ps[1] == android.graphics.Bitmap.class;
                if (boSet) {
                    m.setAccessible(true);
                    hook(m).setId("xtak_bitem_bo_" + m.getName())
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(blockVoidE("banner_bo"));
                    installed++;
                } else if (bmpSet) {
                    m.setAccessible(true);
                    hook(m).setId("xtak_bitem_bmp_" + m.getName())
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(blockVoidE("banner_bmp"));
                    installed++;
                }
            }
            done.add(BANNER_ITEM);
            remaining--;
            log(Log.INFO, TAG, "event=banner_item_hooked hooks=" + installed);
            android.util.Log.e("XTAK2", "banner item hooks=" + installed);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError)) {
                android.util.Log.e("XTAK2", "banner item defer: " + t);
            }
        }
    }

    /** table screen ads: block request trigger and all deliveries (static forms) */
    private void tryInstallTableScreen(ClassLoader cl) {
        try {
            Class<?> mgr = cl.loadClass(TABLE_SCREEN);
            Class<?> adType = cl.loadClass(TABLE_AD_TYPE);
            Class<?> beizi = cl.loadClass(BEIZI_INTERSTITIAL);
            int installed = 0;
            for (Method m : mgr.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                // static forms carry the receiver as ps[0]
                boolean delivery = ps.length == 4 && ps[0] == mgr
                        && ps[1] == int.class && ps[2] == java.util.List.class && ps[3] == adType;
                boolean request = ps.length == 2 && ps[0] == mgr && ps[1] == adType;
                boolean beiziSet = ps.length == 2 && ps[0] == mgr && ps[1] == beizi;
                if (delivery || request || beiziSet) {
                    m.setAccessible(true);
                    hook(m).setId("xtak_table_" + m.getName() + "_" + ps.length)
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(blockVoidE("table"));
                    installed++;
                }
            }
            done.add(TABLE_SCREEN);
            remaining--;
            log(Log.INFO, TAG, "event=table_screen_hooked hooks=" + installed);
            android.util.Log.e("XTAK2", "table screen hooks=" + installed);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError)) {
                android.util.Log.e("XTAK2", "table screen defer: " + t);
            }
        }
    }

    /** home prefetch: block list deliveries */
    private void tryInstallPrefetch(ClassLoader cl) {
        try {
            Class<?> mgr = cl.loadClass(HOME_PREFETCH);
            int installed = 0;
            for (Method m : mgr.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                boolean hasList = false;
                for (Class<?> p : ps) {
                    if (p == java.util.List.class) {
                        hasList = true;
                        break;
                    }
                }
                if (ps.length >= 2 && hasList) {
                    m.setAccessible(true);
                    hook(m).setId("xtak_prefetch_" + m.getName() + "_" + ps.length)
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(blockVoidE("prefetch"));
                    installed++;
                }
            }
            done.add(HOME_PREFETCH);
            remaining--;
            log(Log.INFO, TAG, "event=prefetch_hooked hooks=" + installed);
            android.util.Log.e("XTAK2", "prefetch hooks=" + installed);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError)) {
                android.util.Log.e("XTAK2", "prefetch defer: " + t);
            }
        }
    }

    private XposedInterface.Hooker blockVoidE(final String what) {
        return chain -> {
            flog("KILL " + what + " " + chain.getExecutable().getDeclaringClass().getSimpleName()
                    + "#" + chain.getExecutable().getName());
            return null;
        };
    }

    // ---- glide banner kill -------------------------------------------------

    /** starve the channel-banner ImageViews via Glide into() and target callbacks */
    private void tryInstallGlide(ClassLoader cl) {
        try {
            java.util.Set<Integer> ids = new HashSet<>();
            Class<?> rid = cl.loadClass("com.xtuone.android.syllabus.R$id");
            for (String f : new String[]{"channel_banner_iv", "ad_brann_view", "fake_banner"}) {
                try {
                    java.lang.reflect.Field rf = rid.getDeclaredField(f);
                    ids.add(rf.getInt(null));
                } catch (Throwable ignored) {
                }
            }
            if (ids.isEmpty()) {
                return; // R not ready yet, poll again
            }
            killViewIdsRef.set(ids);
            int hooks = 0;
            // path 1: RequestBuilder.into(...) — every arity, runtime view check
            Class<?> rb = cl.loadClass("com.bumptech.glide.RequestBuilder");
            for (Method m : rb.getMethods()) {
                if (m.getName().equals("into")) {
                    m.setAccessible(true);
                    hook(m).setId("xtak_glide_into_" + m.getParameterTypes().length)
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(glideIntoKill);
                    hooks++;
                }
            }
            // path 2: ImageViewTarget.onResourceReady — covers generic targets
            for (String tn : new String[]{
                    "com.bumptech.glide.request.target.ImageViewTarget",
                    "com.bumptech.glide.request.target.BitmapImageViewTarget",
                    "com.bumptech.glide.request.target.DrawableImageViewTarget"}) {
                try {
                    Class<?> tc = cl.loadClass(tn);
                    for (Method m : tc.getDeclaredMethods()) {
                        if (m.getName().equals("onResourceReady")) {
                            m.setAccessible(true);
                            hook(m).setId("xtak_glide_rr_" + tn.hashCode())
                                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                                    .intercept(glideResourceKill);
                            hooks++;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            if (hooks > 0) {
                done.add("glide");
                remaining--;
                flog("glide hooks=" + hooks + " ids=" + ids);
            }
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError)) {
                flog("glide defer: " + t);
            }
        }
    }

    /** returns the kill-matching View from args, if any */
    private static Object killView(Object[] args) {
        java.util.Set<Integer> ids = killViewIdsRef.get();
        if (ids == null) {
            return null;
        }
        for (Object a : args) {
            if (a instanceof android.view.View && ids.contains(((android.view.View) a).getId())) {
                return a;
            }
        }
        return null;
    }

    private static final java.util.concurrent.atomic.AtomicReference<java.util.Set<Integer>>
            killViewIdsRef = new java.util.concurrent.atomic.AtomicReference<>();

    private final XposedInterface.Hooker glideIntoKill = chain -> {
        try {
            if (killView(chain.getArgs().toArray()) != null) {
                dumpCallerStack();
                flog("KILL glide_into_banner");
                return null;
            }
        } catch (Throwable ignored) {
        }
        return chain.proceed();
    };

    private final XposedInterface.Hooker glideResourceKill = chain -> {
        try {
            Object self = chain.getThisObject();
            if (self != null) {
                java.lang.reflect.Field vf = self.getClass().getField("view");
                vf.setAccessible(true);
                Object v = vf.get(self);
                if (v instanceof android.view.View
                        && killViewIdsRef.get() != null
                        && killViewIdsRef.get().contains(((android.view.View) v).getId())) {
                    dumpCallerStack();
                    flog("KILL glide_resource_banner");
                    return null;
                }
            }
        } catch (NoSuchFieldException ignored) {
        } catch (Throwable t) {
            flog("glide rr check error: " + t);
        }
        return chain.proceed();
    };

    private static final java.util.Set<String> seenUrls =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** log every volley request url (passive) */
    private void tryInstallVolley(ClassLoader cl) {
        try {
            Class<?> req = cl.loadClass("com.android.volley.Request");
            int hooks = 0;
            for (Method m : req.getDeclaredMethods()) {
                if (m.getName().equals("getUrl") && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    hook(m).setId("xtak_volley_url")
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(chain -> {
                                try {
                                    Object r = chain.proceed();
                                    if (r instanceof String) {
                                        String u = (String) r;
                                        if (seenUrls.add(u) && seenUrls.size() < 400) {
                                            flog("VOLLEY " + u);
                                        }
                                    }
                                } catch (Throwable ignored) {
                                }
                                return chain.proceed();
                            });
                    hooks++;
                    break;
                }
            }
            if (hooks > 0) {
                done.add("volley");
                flog("volley url hook ok");
            }
        } catch (Throwable t) {
            flog("volley defer: " + t);
        }
    }

    /** passive trace of wall manager keyword methods */
    private void tryInstallWallTrace(ClassLoader cl) {
        try {
            Class<?> mgr = cl.loadClass(WALL_MANAGER);
            int hooks = 0;
            for (Method m : mgr.getDeclaredMethods()) {
                String mn = m.getName();
                if (mn.equals("abstract") || mn.equals("continue") || mn.equals("interface")
                        || mn.equals("volatile") || mn.equals("strictfp") || mn.equals("finally")
                        || mn.equals("native") || mn.equals("default") || mn.equals("throws")) {
                    m.setAccessible(true);
                    final String mnF = mn;
                    hook(m).setId("xtak_wtrace_" + mn + "_" + m.getParameterTypes().length)
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(chain -> {
                                StringBuilder ps = new StringBuilder();
                                for (Object a : chain.getArgs()) {
                                    ps.append(a == null ? "null" : a.getClass().getSimpleName()).append(',');
                                }
                                flog("WALL " + mnF + "(" + ps + ")");
                                return chain.proceed();
                            });
                    hooks++;
                }
            }
            if (hooks > 0) {
                done.add("walltrace");
                flog("wall trace hooks=" + hooks);
            }
        } catch (Throwable t) {
            flog("wall trace defer: " + t);
        }
    }

    /** block the ad-space config response so the home banner never renders */
    private void tryInstallAdSpaceKill(ClassLoader cl) {
        try {
            Class<?> run = cl.loadClass(
                    "com.android.volley.ExecutorDelivery$ResponseDeliveryRunnable");
            Method runM = run.getDeclaredMethod("run");
            runM.setAccessible(true);
            java.lang.reflect.Field reqF = null;
            for (java.lang.reflect.Field f : run.getDeclaredFields()) {
                if (f.getType().getName().equals("com.android.volley.Request")) {
                    f.setAccessible(true);
                    reqF = f;
                    break;
                }
            }
            if (reqF == null) {
                flog("adspace kill: no request field");
                return;
            }
            final java.lang.reflect.Field reqFF = reqF;
            hook(runM).setId("xtak_adspace_kill")
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept(chain -> {
                        try {
                            Object self = chain.getThisObject();
                            Object req = reqFF.get(self);
                            if (req != null) {
                                Object url = req.getClass().getMethod("getUrl").invoke(req);
                                if (url instanceof String
                                        && ((String) url).contains("startAdSpace")) {
                                    flog("KILL adspace_config " + url);
                                    return null;
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                        return chain.proceed();
                    });
            done.add("adspace");
            flog("adspace kill installed");
        } catch (Throwable t) {
            flog("adspace defer: " + t);
        }
    }

    /** block fresco image setting on the home banner views */
    private void tryInstallFresco(ClassLoader cl) {
        try {
            java.util.Set<Integer> ids = new HashSet<>();
            Class<?> rid = cl.loadClass("com.xtuone.android.syllabus.R$id");
            for (String f : new String[]{"brand_banner_image", "treehole_banner_image",
                    "channel_banner_iv", "ad_brann_view", "fake_banner"}) {
                try {
                    ids.add(rid.getDeclaredField(f).getInt(null));
                } catch (Throwable ignored) {
                }
            }
            killViewIdsRef.set(ids);
            int hooks = 0;
            Class<?> sdv = cl.loadClass("com.facebook.drawee.view.SimpleDraweeView");
            for (Method m : sdv.getMethods()) {
                String mn = m.getName();
                if (mn.equals("setImageURI") || mn.equals("setImageBitmap")
                        || mn.equals("setImageDrawable") || mn.equals("setController")) {
                    m.setAccessible(true);
                    hook(m).setId("xtak_fresco_" + mn + "_" + m.getParameterTypes().length)
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(frescoKill);
                    hooks++;
                }
            }
            Class<?> dv = cl.loadClass("com.facebook.drawee.view.DraweeView");
            for (Method m : dv.getDeclaredMethods()) {
                if (m.getName().equals("setController")) {
                    m.setAccessible(true);
                    hook(m).setId("xtak_fresco_ctrl")
                            .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                            .intercept(frescoKill);
                    hooks++;
                }
            }
            if (hooks > 0) {
                done.add("fresco");
                remaining--;
                flog("fresco hooks=" + hooks + " ids=" + ids);
            }
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError)) {
                flog("fresco defer: " + t);
            }
        }
        // hide the banner container itself once ids are known
        tryInstallAdContainerHide(cl);
    }

    /** hide the home banner AdContainer (and its parent include) */
    private void tryInstallAdContainerHide(ClassLoader cl) {
        try {
            Class<?> rid = cl.loadClass("com.xtuone.android.syllabus.R$id");
            final int adContainerId;
            final int bannerParentId;
            try {
                adContainerId = rid.getDeclaredField("rlyt_advertising_content").getInt(null);
                bannerParentId = rid.getDeclaredField("campus_top_banner_ad").getInt(null);
            } catch (Throwable t) {
                return;
            }
            Class<?> ac = cl.loadClass("com.xtuone.android.friday.widget.AdContainer");
            for (Constructor<?> k : ac.getDeclaredConstructors()) {
                k.setAccessible(true);
                hook(k).setId("xtak_ac_ctor_" + k.getParameterTypes().length)
                        .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                        .intercept(chain -> {
                            Object self = chain.getThisObject();
                            if (self instanceof android.view.View) {
                                final android.view.View v = (android.view.View) self;
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    try {
                                        int vid = v.getId();
                                        if (vid == adContainerId) {
                                            v.setVisibility(android.view.View.GONE);
                                            android.view.View p = (android.view.View) v.getParent();
                                            if (p != null && p.getId() == bannerParentId) {
                                                p.setVisibility(android.view.View.GONE);
                                            }
                                            flog("HIDE banner container");
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                }, 250);
                            }
                            return chain.proceed();
                        });
            }
            done.add("achide");
            flog("ad container hide installed");
        } catch (Throwable t) {
            flog("achide defer: " + t);
        }
    }

    private final XposedInterface.Hooker frescoKill = chain -> {
        try {
            Object self = chain.getThisObject();
            if (self instanceof android.view.View) {
                java.util.Set<Integer> ids = killViewIdsRef.get();
                if (ids != null && ids.contains(((android.view.View) self).getId())) {
                    flog("KILL fresco_banner " + chain.getExecutable().getName());
                    return null;
                }
            }
        } catch (Throwable ignored) {
        }
        return chain.proceed();
    };

    /** hide the course-table floating skin-ad chest */
    private void tryInstallCourseSkin(ClassLoader cl) {
        try {
            Class<?> cls = cl.loadClass(
                    "com.xtuone.android.friday.treehole.ui.CourseSkinAdEnterView");
            final int enterId;
            try {
                enterId = cl.loadClass("com.xtuone.android.syllabus.R$id")
                        .getDeclaredField("ad_enter_view").getInt(null);
            } catch (Throwable t) {
                flog("courseskin: id not ready");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (Method m : cls.getDeclaredMethods()) {
                sb.append(m.getName()).append('(')
                        .append(m.getParameterTypes().length).append(") | ");
            }
            flog("courseskin methods: " + sb);
            for (Constructor<?> k : cls.getDeclaredConstructors()) {
                k.setAccessible(true);
                hook(k).setId("xtak_cs_ctor_" + k.getParameterTypes().length)
                        .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                        .intercept(chain -> {
                            Object self = chain.getThisObject();
                            if (self instanceof android.view.View) {
                                final android.view.View v = (android.view.View) self;
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    try {
                                        if (v.getId() == enterId) {
                                            v.setVisibility(android.view.View.GONE);
                                            flog("HIDE course skin chest");
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                }, 250);
                            }
                            return chain.proceed();
                        });
            }
            done.add("courseskin");
            flog("course skin hide installed");
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError)) {
                flog("courseskin defer: " + t);
            }
        }
    }

    private static void dumpCallerStack() {
        try {
            StringBuilder st = new StringBuilder();
            StackTraceElement[] frames = new Throwable().getStackTrace();
            for (int i = 1; i < Math.min(frames.length, 25); i++) {
                String cn = frames[i].getClassName();
                if (cn.startsWith("com.xtuone") || cn.startsWith("xyz.adscope")
                        || cn.startsWith("com.bumptech") || cn.startsWith("com.qq")
                        || cn.startsWith("com.baidu") || cn.startsWith("com.meishu")) {
                    st.append(cn).append('.').append(frames[i].getMethodName())
                            .append(':').append(frames[i].getLineNumber()).append(" <- ");
                }
            }
            flog("stack=" + st);
        } catch (Throwable ignored) {
        }
    }
}
