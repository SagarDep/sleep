package me.zsj.sleep;

import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by zsj on 2016/8/6.
 */
public class DoubleTapHook implements IXposedHookLoadPackage {

    private static final String PHONE_STATUS_BAR_CLASS = "com.android.systemui.statusbar.phone.PhoneStatusBar";
    private static final String TAG = "Sleep";

    private boolean homePressed = false;

    private View homeButtonView;
    private MotionEvent motionEvent;

    private Context context;
    private Handler handler;

    private View.OnTouchListener homeActionListener;

    private Runnable homePressedStateRunnable = new Runnable() {
        @Override
        public void run() {
            homePressed = false;
            homeActionListener.onTouch(homeButtonView, motionEvent);
        }
    };


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            XposedHelpers.findAndHookMethod(PHONE_STATUS_BAR_CLASS, lpparam.classLoader,
                    "prepareNavigationBarView", prepareNavigationBarViewHook);
        } catch (NoSuchMethodError e) {
            // CM takes a boolean parameter
            XposedHelpers.findAndHookMethod(PHONE_STATUS_BAR_CLASS, lpparam.classLoader,
                    "prepareNavigationBarView", boolean.class, prepareNavigationBarViewHook);
        }
    }

    private XC_MethodHook prepareNavigationBarViewHook = new XC_MethodHook() {

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            super.afterHookedMethod(param);

            homeActionListener = (View.OnTouchListener) XposedHelpers.getObjectField(
                    param.thisObject, "mHomeActionListener");
            handler = (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler");
            context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
            Object navigationBarView = XposedHelpers.getObjectField(param.thisObject, "mNavigationBarView");
            homeButtonView = (View) XposedHelpers.callMethod(navigationBarView, "getHomeButton");

            homeButtonView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        motionEvent = event;
                        if (!homePressed) {
                            XposedBridge.log(TAG + " Home tap: execute......");
                            homePressed = true;
                            handler.postDelayed(homePressedStateRunnable, 400);
                        } else {
                            XposedBridge.log(TAG + " Double tap: detected....");
                            handler.removeCallbacks(homePressedStateRunnable);
                            homePressed = false;
                            goToSleep();
                        }
                    }
                    return false;
                }
            });
        }
    };

    private void goToSleep() {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            XposedHelpers.callMethod(pm, "goToSleep", SystemClock.uptimeMillis());
        } catch (Exception e) {
            XposedBridge.log(TAG + e);
        }
    }

}
