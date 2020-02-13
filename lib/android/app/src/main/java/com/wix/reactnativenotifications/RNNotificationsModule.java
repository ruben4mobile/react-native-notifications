package com.wix.reactnativenotifications;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.wix.reactnativenotifications.core.AppLifecycleFacadeHolder;
import com.wix.reactnativenotifications.core.NotificationIntentAdapter;
import com.wix.reactnativenotifications.core.ReactAppLifecycleFacade;
import com.wix.reactnativenotifications.core.notification.IPushNotification;
import com.wix.reactnativenotifications.core.notification.PushNotification;
import com.wix.reactnativenotifications.core.notificationdrawer.IPushNotificationsDrawer;
import com.wix.reactnativenotifications.core.notificationdrawer.PushNotificationsDrawer;
import com.wix.reactnativenotifications.fcm.FcmInstanceIdRefreshHandlerService;

import static com.wix.reactnativenotifications.Defs.LOGTAG;

public class RNNotificationsModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    public RNNotificationsModule(Application application, ReactApplicationContext reactContext) {
        super(reactContext);
        if (AppLifecycleFacadeHolder.get() instanceof ReactAppLifecycleFacade) {
            ((ReactAppLifecycleFacade) AppLifecycleFacadeHolder.get()).init(reactContext);
        }

        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "RNBridgeModule";
    }

    @Override
    public void initialize() {
        Log.d(LOGTAG, "Native module init");
        startFcmIntentService(FcmInstanceIdRefreshHandlerService.EXTRA_IS_APP_INIT);

        final IPushNotificationsDrawer notificationsDrawer = PushNotificationsDrawer.get(getReactApplicationContext().getApplicationContext());
        notificationsDrawer.onAppInit();
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {

    }

    @Override
    public void onNewIntent(Intent intent) {
        if (NotificationIntentAdapter.canHandleIntent(intent)) {
            Bundle notificationData = intent.getExtras();
            final IPushNotification notification = PushNotification.get(getReactApplicationContext().getApplicationContext(), notificationData);
            if (notification != null) {
                notification.onOpened();
            }
        }
    }

    @ReactMethod
    public void refreshToken() {
        Log.d(LOGTAG, "Native method invocation: refreshToken()");
        startFcmIntentService(FcmInstanceIdRefreshHandlerService.EXTRA_MANUAL_REFRESH);
    }

    @ReactMethod
    public void getInitialNotification(Promise promise) {
        WritableMap notificationOpenMap = null;
        if (getCurrentActivity() != null) {
            notificationOpenMap = parseIntentForRemoteNotification(getCurrentActivity().getIntent());
        }
        promise.resolve(notificationOpenMap);
    }

    private WritableMap parseIntentForRemoteNotification(Intent intent) {
        // Check if FCM data exists
        if (intent.getExtras() == null || !intent.hasExtra("google.message_id")) {
            return null;
        }

        Bundle extras = intent.getExtras();

        WritableMap notificationMap = Arguments.createMap();
        WritableMap dataMap = Arguments.createMap();

        for (String key : extras.keySet()) {
            if (key.equals("google.message_id")) {
                notificationMap.putString("notificationId", extras.getString(key));
            } else if (key.equals("collapse_key")
                    || key.equals("from")
                    || key.equals("google.sent_time")
                    || key.equals("google.ttl")
                    || key.equals("_fbSourceApplicationHasBeenSet")) {
                // ignore known unneeded fields
            } else {
                dataMap.putString(key, extras.getString(key));
            }
        }
        notificationMap.putMap("data", dataMap);

        WritableMap notificationOpenMap = Arguments.createMap();
        notificationOpenMap.putString("action", intent.getAction());
        notificationOpenMap.putMap("notification", notificationMap);

        return notificationOpenMap;
    }

    @ReactMethod
    public void postLocalNotification(ReadableMap notificationPropsMap, int notificationId) {
        Log.d(LOGTAG, "Native method invocation: postLocalNotification");
        final Bundle notificationProps = Arguments.toBundle(notificationPropsMap);
        final IPushNotification pushNotification = PushNotification.get(getReactApplicationContext().getApplicationContext(), notificationProps);
        pushNotification.onPostRequest(notificationId);
    }

    @ReactMethod
    public void cancelLocalNotification(int notificationId) {
        IPushNotificationsDrawer notificationsDrawer = PushNotificationsDrawer.get(getReactApplicationContext().getApplicationContext());
        notificationsDrawer.onNotificationClearRequest(notificationId);
    }

    @ReactMethod
    public void setCategories(ReadableArray categories) {

    }

    public void cancelDeliveredNotification(String tag, int notificationId) {
        IPushNotificationsDrawer notificationsDrawer = PushNotificationsDrawer.get(getReactApplicationContext().getApplicationContext());
        notificationsDrawer.onNotificationClearRequest(tag, notificationId);
    }

    @ReactMethod
    public void isRegisteredForRemoteNotifications(Promise promise) {
        boolean hasPermission = NotificationManagerCompatFacade.from(getReactApplicationContext()).areNotificationsEnabled();
        promise.resolve(new Boolean(hasPermission));
    }

    @ReactMethod
    void removeAllDeliveredNotifications() {
        IPushNotificationsDrawer notificationsDrawer = PushNotificationsDrawer.get(getReactApplicationContext().getApplicationContext());
        notificationsDrawer.onAllNotificationsClearRequest();
    }

    protected void startFcmIntentService(String extraFlag) {
        final Context appContext = getReactApplicationContext().getApplicationContext();
        final Intent tokenFetchIntent = new Intent(appContext, FcmInstanceIdRefreshHandlerService.class);
        tokenFetchIntent.putExtra(extraFlag, true);
        appContext.startService(tokenFetchIntent);
    }
}
