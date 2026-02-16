package com.slm.share;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;

import androidx.core.content.FileProvider;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

public class SLMShare extends CordovaPlugin {

    private static final String TAG = "SLMShare";
    private static final int SHARE_REQUEST = 300;

    private CallbackContext shareCallback;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case "share":
                share(args.optJSONObject(0), callbackContext);
                return true;
            case "shareToApp":
                shareToApp(args.optJSONObject(0), callbackContext);
                return true;
            case "getAvailableApps":
                getAvailableApps(callbackContext);
                return true;
            case "shareScreenshot":
                shareScreenshot(args.optJSONObject(0), callbackContext);
                return true;
            case "saveToGallery":
                saveToGallery(args.optString(0, ""), callbackContext);
                return true;
            default:
                return false;
        }
    }

    // ============================================
    // share
    // ============================================

    private void share(JSONObject options, CallbackContext callbackContext) {
        if (options == null) options = new JSONObject();

        final String text = options.optString("text", null);
        final String url = options.optString("url", null);
        final String imageBase64 = options.optString("image", null);
        final String title = options.optString("title", "Compartir");

        final Activity activity = cordova.getActivity();
        final JSONObject opts = options;

        activity.runOnUiThread(() -> {
            try {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);

                // Build share text
                StringBuilder shareText = new StringBuilder();
                if (text != null && !text.isEmpty()) shareText.append(text);
                if (url != null && !url.isEmpty()) {
                    if (shareText.length() > 0) shareText.append(" ");
                    shareText.append(url);
                }

                if (imageBase64 != null && !imageBase64.isEmpty()) {
                    Uri imageUri = saveBase64ToCache(imageBase64, activity);
                    if (imageUri != null) {
                        shareIntent.setType("image/*");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                } else {
                    shareIntent.setType("text/plain");
                }

                if (shareText.length() > 0) {
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
                }

                shareCallback = callbackContext;
                Intent chooser = Intent.createChooser(shareIntent, title);
                cordova.startActivityForResult(this, chooser, SHARE_REQUEST);

            } catch (Exception e) {
                Log.e(TAG, "Share error: " + e.getMessage());
                callbackContext.error("Error al compartir: " + e.getMessage());
            }
        });
    }

    // ============================================
    // shareToApp
    // ============================================

    private void shareToApp(JSONObject options, CallbackContext callbackContext) {
        if (options == null) options = new JSONObject();

        final String app = options.optString("app", "");
        final String text = options.optString("text", "");
        final String url = options.optString("url", null);
        final String imageBase64 = options.optString("image", null);
        final String phoneNumber = options.optString("phoneNumber", null);

        final Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {
            try {
                boolean opened = false;

                switch (app) {
                    case "whatsapp": {
                        String shareText = text;
                        if (url != null) shareText += " " + url;

                        if (phoneNumber != null) {
                            // Direct to number
                            String waUrl = "https://wa.me/" + phoneNumber.replaceAll("[^0-9]", "") + "?text=" + Uri.encode(shareText);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(waUrl));
                            activity.startActivity(intent);
                            opened = true;
                        } else {
                            Intent intent = new Intent(Intent.ACTION_SEND);
                            intent.setPackage("com.whatsapp");
                            intent.setType("text/plain");
                            intent.putExtra(Intent.EXTRA_TEXT, shareText);

                            if (imageBase64 != null && !imageBase64.isEmpty()) {
                                Uri imageUri = saveBase64ToCache(imageBase64, activity);
                                if (imageUri != null) {
                                    intent.setType("image/*");
                                    intent.putExtra(Intent.EXTRA_STREAM, imageUri);
                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                }
                            }
                            activity.startActivity(intent);
                            opened = true;
                        }
                        break;
                    }

                    case "telegram": {
                        String shareText = text;
                        if (url != null) shareText += " " + url;

                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setPackage("org.telegram.messenger");
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, shareText);

                        if (imageBase64 != null && !imageBase64.isEmpty()) {
                            Uri imageUri = saveBase64ToCache(imageBase64, activity);
                            if (imageUri != null) {
                                intent.setType("image/*");
                                intent.putExtra(Intent.EXTRA_STREAM, imageUri);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            }
                        }
                        activity.startActivity(intent);
                        opened = true;
                        break;
                    }

                    case "instagram": {
                        if (imageBase64 != null && !imageBase64.isEmpty()) {
                            Uri imageUri = saveBase64ToCache(imageBase64, activity);
                            if (imageUri != null) {
                                Intent intent = new Intent("com.instagram.share.ADD_TO_STORY");
                                intent.setPackage("com.instagram.android");
                                intent.setDataAndType(imageUri, "image/*");
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                activity.startActivity(intent);
                                opened = true;
                            }
                        } else {
                            Intent intent = activity.getPackageManager().getLaunchIntentForPackage("com.instagram.android");
                            if (intent != null) {
                                activity.startActivity(intent);
                                opened = true;
                            }
                        }
                        break;
                    }

                    case "facebook": {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setPackage("com.facebook.katana");
                        intent.setType("text/plain");
                        if (url != null) intent.putExtra(Intent.EXTRA_TEXT, url);
                        else intent.putExtra(Intent.EXTRA_TEXT, text);
                        activity.startActivity(intent);
                        opened = true;
                        break;
                    }

                    case "twitter": {
                        String shareText = text;
                        if (url != null) shareText += " " + url;

                        Intent intent = new Intent(Intent.ACTION_SEND);
                        // Try X (new Twitter) first
                        intent.setPackage("com.twitter.android");
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, shareText);
                        activity.startActivity(intent);
                        opened = true;
                        break;
                    }

                    case "email": {
                        String shareText = text;
                        if (url != null) shareText += "\n" + url;

                        Intent intent = new Intent(Intent.ACTION_SENDTO);
                        intent.setData(Uri.parse("mailto:"));
                        intent.putExtra(Intent.EXTRA_TEXT, shareText);
                        activity.startActivity(intent);
                        opened = true;
                        break;
                    }

                    case "sms": {
                        String shareText = text;
                        if (url != null) shareText += " " + url;

                        String smsUri = "sms:";
                        if (phoneNumber != null) smsUri += phoneNumber;
                        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(smsUri));
                        intent.putExtra("sms_body", shareText);
                        activity.startActivity(intent);
                        opened = true;
                        break;
                    }

                    default:
                        callbackContext.error("App no soportada: " + app);
                        return;
                }

                if (opened) {
                    JSONObject result = new JSONObject();
                    result.put("completed", true);
                    result.put("app", app);
                    callbackContext.success(result);
                } else {
                    callbackContext.error(app + " no esta instalada o no se pudo abrir");
                }

            } catch (android.content.ActivityNotFoundException e) {
                callbackContext.error(app + " no esta instalada");
            } catch (Exception e) {
                Log.e(TAG, "shareToApp error: " + e.getMessage());
                callbackContext.error("Error al compartir: " + e.getMessage());
            }
        });
    }

    // ============================================
    // getAvailableApps
    // ============================================

    private void getAvailableApps(CallbackContext callbackContext) {
        Activity activity = cordova.getActivity();
        PackageManager pm = activity.getPackageManager();

        try {
            JSONObject result = new JSONObject();
            result.put("whatsapp", isAppInstalled(pm, "com.whatsapp"));
            result.put("telegram", isAppInstalled(pm, "org.telegram.messenger"));
            result.put("instagram", isAppInstalled(pm, "com.instagram.android"));
            result.put("facebook", isAppInstalled(pm, "com.facebook.katana"));
            result.put("twitter", isAppInstalled(pm, "com.twitter.android"));
            result.put("email", canSendEmail(pm));
            result.put("sms", canSendSMS(pm));
            callbackContext.success(result);
        } catch (JSONException e) {
            callbackContext.error("Error detectando apps: " + e.getMessage());
        }
    }

    // ============================================
    // shareScreenshot
    // ============================================

    private void shareScreenshot(JSONObject options, CallbackContext callbackContext) {
        if (options == null) options = new JSONObject();

        final boolean shouldShare = options.optBoolean("share", false);
        final boolean returnBase64 = options.optBoolean("returnBase64", true);

        final Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {
            try {
                // Find the WebView
                android.view.View webView = this.webView.getView();
                if (webView == null) {
                    callbackContext.error("No se pudo acceder al WebView");
                    return;
                }

                Bitmap bitmap = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                webView.draw(canvas);

                if (shouldShare) {
                    // Save to cache and share
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                    String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                    Uri imageUri = saveBase64ToCache(base64, activity);
                    if (imageUri != null) {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("image/*");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        shareCallback = callbackContext;
                        Intent chooser = Intent.createChooser(shareIntent, "Compartir captura");
                        cordova.startActivityForResult(this, chooser, SHARE_REQUEST);
                    } else {
                        callbackContext.error("Error guardando captura temporal");
                    }
                } else {
                    JSONObject result = new JSONObject();
                    result.put("completed", true);

                    if (returnBase64) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                        result.put("base64", Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP));
                    }

                    callbackContext.success(result);
                }

                bitmap.recycle();

            } catch (Exception e) {
                Log.e(TAG, "Screenshot error: " + e.getMessage());
                callbackContext.error("Error capturando pantalla: " + e.getMessage());
            }
        });
    }

    // ============================================
    // saveToGallery
    // ============================================

    private void saveToGallery(String base64, CallbackContext callbackContext) {
        if (base64 == null || base64.isEmpty()) {
            callbackContext.error("Base64 invalido o vacio");
            return;
        }

        cordova.getThreadPool().execute(() -> {
            try {
                byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                if (bitmap == null) {
                    callbackContext.error("No se pudo decodificar la imagen");
                    return;
                }

                Activity activity = cordova.getActivity();
                String filename = "SLM_" + System.currentTimeMillis() + ".png";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ use MediaStore
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SLM");

                    Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        OutputStream os = activity.getContentResolver().openOutputStream(uri);
                        if (os != null) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                            os.close();
                        }
                    }

                    JSONObject result = new JSONObject();
                    result.put("saved", true);
                    if (uri != null) result.put("path", uri.toString());
                    callbackContext.success(result);
                } else {
                    // Legacy
                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    File slmDir = new File(picturesDir, "SLM");
                    if (!slmDir.exists()) slmDir.mkdirs();

                    File file = new File(slmDir, filename);
                    FileOutputStream fos = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();

                    // Notify gallery
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(Uri.fromFile(file));
                    activity.sendBroadcast(mediaScanIntent);

                    JSONObject result = new JSONObject();
                    result.put("saved", true);
                    result.put("path", file.getAbsolutePath());
                    callbackContext.success(result);
                }

                bitmap.recycle();

            } catch (Exception e) {
                Log.e(TAG, "saveToGallery error: " + e.getMessage());
                callbackContext.error("Error guardando imagen: " + e.getMessage());
            }
        });
    }

    // ============================================
    // Activity Result
    // ============================================

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == SHARE_REQUEST && shareCallback != null) {
            try {
                JSONObject result = new JSONObject();
                result.put("completed", resultCode == Activity.RESULT_OK);
                shareCallback.success(result);
            } catch (JSONException e) {
                shareCallback.error("Error procesando resultado");
            }
            shareCallback = null;
        }
    }

    // ============================================
    // Helpers
    // ============================================

    private Uri saveBase64ToCache(String base64, Activity activity) {
        try {
            byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);
            File cacheDir = new File(activity.getCacheDir(), "slm_share");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            File imageFile = new File(cacheDir, "share_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(imageFile);
            fos.write(imageBytes);
            fos.close();

            String authority = activity.getPackageName() + ".slm.share.fileprovider";
            return FileProvider.getUriForFile(activity, authority, imageFile);
        } catch (Exception e) {
            Log.e(TAG, "saveBase64ToCache error: " + e.getMessage());
            return null;
        }
    }

    private boolean isAppInstalled(PackageManager pm, String packageName) {
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean canSendEmail(PackageManager pm) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        return !resolveInfos.isEmpty();
    }

    private boolean canSendSMS(PackageManager pm) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("sms:"));
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        return !resolveInfos.isEmpty();
    }
}
