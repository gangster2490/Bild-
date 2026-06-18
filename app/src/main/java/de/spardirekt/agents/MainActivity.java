package de.spardirekt.agents;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.content.Intent;
import android.net.Uri;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // fullscreen dark
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.parseColor("#0a0a0a"));
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient(){
            @Override
            public void onPermissionRequest(PermissionRequest req){
                req.grant(req.getResources());
            }
            @Override
            public boolean onShowFileChooser(WebView v,
                    ValueCallback<Uri[]> cb,
                    WebChromeClient.FileChooserParams p){
                filePathCallback = cb;
                Intent i = p.createIntent();
                startActivityForResult(i, FILE_CHOOSER_REQUEST);
                return true;
            }
        });

        // hide system UI bars
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data){
        if(req == FILE_CHOOSER_REQUEST){
            if(filePathCallback == null) return;
            Uri[] results = null;
            if(res == Activity.RESULT_OK && data != null){
                String str = data.getDataString();
                if(str != null){
                    results = new Uri[]{Uri.parse(str)};
                } else if(data.getClipData() != null){
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for(int i=0;i<count;i++)
                        results[i] = data.getClipData().getItemAt(i).getUri();
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed(){
        if(webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
