package ir.javadzobeidi.cafebazaar_flutter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry;
import ir.javadzobeidi.cafebazaar_flutter.util.IabException;
import ir.javadzobeidi.cafebazaar_flutter.util.IabHelper;
import ir.javadzobeidi.cafebazaar_flutter.util.IabResult;
import ir.javadzobeidi.cafebazaar_flutter.util.Inventory;
import ir.javadzobeidi.cafebazaar_flutter.util.Purchase;

public class CafebazaarFlutterPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {

    // Debug tag, for logging
    private static final String TAG = "cafebazaar_Plugin";

    // Set market
    private String market = "google";

    // SKUs for our products: the premium upgrade (non-consumable)
    private String SKU = "";
    private String payLoad = "";
    private boolean consumption = false;


    // (arbitrary) request code for the purchase flow
    private static final int RC_REQUEST = 10001;

    // The helper object
    private IabHelper mHelper;

    private MethodChannel.Result pendingResult;

    private MethodChannel channel;
    private Activity activity;

    public static void registerWith(PluginRegistry.Registrar registrar) {
        CafebazaarFlutterPlugin plugin = new CafebazaarFlutterPlugin();
        plugin.activity = registrar.activity();
        plugin.setupMethodChannel(registrar.messenger());
        plugin.setupResultListener(registrar, null);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        setupMethodChannel(binding.getBinaryMessenger());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        channel = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        setupResultListener(null, binding);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

    }

    @Override
    public void onDetachedFromActivity() {

    }

    private void setupMethodChannel(BinaryMessenger messenger) {
        channel = new MethodChannel(messenger, "cafebazaar_flutter");
        channel.setMethodCallHandler(this);
    }

    private void setupResultListener(PluginRegistry.Registrar registrar, ActivityPluginBinding activityBinding) {
        if (registrar != null) {
            registrar.addActivityResultListener(new PluginRegistry.ActivityResultListener() {
                @Override
                public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
                    activityResult(requestCode, resultCode, data);
                    return false;
                }
            });
        } else if (activityBinding != null) {
            activityBinding.addActivityResultListener(new PluginRegistry.ActivityResultListener() {
                @Override
                public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
                    activityResult(requestCode, resultCode, data);
                    return false;
                }
            });
        }
    }

    @Override
    public void onMethodCall(MethodCall call, @NonNull MethodChannel.Result result) {
        pendingResult = result;
        String payload;
        String sku;

        switch (call.method) {
            case "init":
                market = call.argument("market");
                break;
            case "initPay":
                onDestroy();
                String rsaKey = call.argument("rsaKey");
                boolean debugMode = call.<Boolean>argument("debugMode");
                initPay(rsaKey, debugMode, result);
                break;
            case "referralToProgram": {
                String packageName = call.argument("packageName");
                referralToProgram(packageName);
                break;
            }
            case "referralToComment": {
                String packageName = call.argument("packageName");
                referralToComment(packageName);
                break;
            }
            case "referralToDeveloperPage":
                String developerId = call.argument("developerId");
                referralToDeveloperPage(developerId);
                break;
            case "referralToLogin":
                referralToLogin();
                break;
            case "dispose":
                onDestroy();
                break;
            case "launchPurchaseFlow":
                sku = call.argument("productKey");
                payload = call.argument("payload");
                boolean consumption = call.<Boolean>argument("consumption");
                launchPurchaseFlow(sku, payload, consumption);
                break;
            case "getPurchase":
                sku = call.argument("sku");
                getPurchase(sku);
                break;
            case "queryInventoryAsync":
                sku = call.argument("sku");
                queryInventoryAsync(sku);
                break;
            case "verifyDeveloperPayload":
                verifyDeveloperPayload();
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void referralToProgram(String packageName) {
        Intent intent = new Intent(Intent.ACTION_VIEW);

        if (market.equalsIgnoreCase("bazaar")) {
            intent.setData(Uri.parse("bazaar://details?id=" + packageName));
            intent.setPackage("com.farsitel.bazaar");
        } else if (market.equalsIgnoreCase("myket")) {
            intent.setData(Uri.parse("myket://details?id=" + packageName));
        }

        activity.startActivity(intent);
    }

    private void referralToComment(String packageName) {
        Intent intent = new Intent(Intent.ACTION_EDIT);

        if (market.equalsIgnoreCase("bazaar")) {
            intent.setData(Uri.parse("bazaar://details?id=" + packageName));
            intent.setPackage("com.farsitel.bazaar");
        } else if (market.equalsIgnoreCase("myket")) {
            intent.setData(Uri.parse("myket://comment?id=" + packageName));
        }

        activity.startActivity(intent);
    }

    private void referralToDeveloperPage(String developerId) {
        Intent intent = new Intent(Intent.ACTION_VIEW);

        if (market.equalsIgnoreCase("bazaar")) {
            intent.setData(Uri.parse("bazaar://collection?slug=by_author&aid=" + developerId));
            intent.setPackage("com.farsitel.bazaar");
        } else if (market.equalsIgnoreCase("myket")) {
            intent.setData(Uri.parse("myket://developer/" + developerId));
        }
        activity.startActivity(intent);
    }

    private void referralToLogin() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("bazaar://login"));
        intent.setPackage("com.farsitel.bazaar");
        activity.startActivity(intent);
    }


    private void initPay(String rsaKey, boolean debugMode, final MethodChannel.Result methodResult) {
        mHelper = new IabHelper(activity, rsaKey, market);
        mHelper.enableDebugLogging(debugMode);
        // Log.d(TAG, "Starting setup.");
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                // Log.d(TAG, "Setup finished.");
                if (!result.isSuccess()) {
                    //   Log.d(TAG, "Problem setting up In-app Billing: " + result);
                    methodResult.error("Setup finished Error", "Problem setting up In-app Billing: " + result, null);
                }
                methodResult.success(true);
            }
        });
    }

    private void onDestroy() {
        if (mHelper != null) mHelper.dispose();
        mHelper = null;
    }

    private IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            if (mHelper == null) return;
            // Log.d(TAG, "Query inventory finished.");
            if (result.isFailure()) {
                // Log.d(TAG, "Failed to query inventory: " + result);
                pendingResult.error("Inventory Listener Error", "Failed to query inventory: " + result, null);
                return;
            }
            pendingResult.success(inventory.getSkuDetails(SKU) + "");
            // Log.d(TAG, "Initial inventory query finished; enabling main UI.");
        }
    };

    private void queryInventoryAsync(String sku) {
        List<String> additionalSkuList = new ArrayList<>();
        additionalSkuList.add(sku);
        additionalSkuList.add("ww");
        SKU = sku;
        mHelper.queryInventoryAsync(true, additionalSkuList, mGotInventoryListener);
    }

    private IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            if (mHelper == null) return;
            JSONObject obj = new JSONObject();
            try {
                if (result.isFailure()) {
                    obj.put("isFailure", result.isFailure());
                    obj.put("response", result.getResponse());
                    obj.put("message", result.getMessage());
                    obj.put("purchase", null);
                    pendingResult.success(obj.toString());
                    return;
                }
                obj.put("isSuccess", result.isSuccess());
                obj.put("response", result.getResponse());
                obj.put("message", result.getMessage());
                obj.put("purchase", purchase.getOriginalJson());
                payLoad = purchase.getDeveloperPayload();
                pendingResult.success(obj.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // Log.d(TAG, "Success purchasing: " + result);

            if (consumption)
                mHelper.consumeAsync(purchase, mConsumeFinishedListener);
        }
    };


    // Called when consumption is complete
    private IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            // Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);
            if (mHelper == null) return;
            if (result.isSuccess()) {
                //  Log.d(TAG, "Consumption successful. Provisioning.");
            } else {
                //   Log.d(TAG, "Error while consuming: " + result);
            }
            //  Log.d(TAG, "End consumption flow.");
        }
    };


    private void getPurchase(String sku) {
        List<String> additionalSkuList = new ArrayList<>();
        additionalSkuList.add(sku);
        try {
            Purchase gasPurchase = mHelper.queryInventory(false, additionalSkuList).getPurchase(sku);
            if (gasPurchase != null) {
                pendingResult.success(gasPurchase.getOriginalJson());
            } else
                pendingResult.success(null);
        } catch (IabException e) {
            e.printStackTrace();
            pendingResult.error("get_purchase_error", e.getMessage(), null);
        }
    }


    private void activityResult(int requestCode, int resultCode, Intent data) {
        // Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (mHelper == null) return;
        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // Log.d(TAG, "onActivityResult " + data);
        } else {
            // Log.d(TAG, "onActivityResult handled by IABUtil." + data);
        }
    }


    private void launchPurchaseFlow(String productKey, String payload, boolean consumption) {
        Log.d(TAG, "launchPurchaseFlow");
        SKU = productKey;
        this.consumption = consumption;
        mHelper.launchPurchaseFlow(activity, productKey, RC_REQUEST,
                mPurchaseFinishedListener, payload);
    }


    private void verifyDeveloperPayload() {
        pendingResult.success(payLoad);
    }

}
