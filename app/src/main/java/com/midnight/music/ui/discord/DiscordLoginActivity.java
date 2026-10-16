package com.midnight.music.ui.discord;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayout;
import com.midnight.music.data.discord.DiscordRpcManager;
import com.midnight.music.data.discord.DiscordUserProfile;
import com.midnight.music.databinding.ActivityDiscordLoginBinding;

public class DiscordLoginActivity extends AppCompatActivity {
    private static final String TAG = "DiscordLoginActivity";
    private static final String DISCORD_LOGIN_URL = "https://discord.com/login";
    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private ActivityDiscordLoginBinding binding;
    private DiscordRpcManager rpcManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPollingToken = false;
    private boolean isTokenCaptured = false;

    // JavaScript snippet reconstructed from Animeze to retrieve token from LocalStorage or Webpack chunk
    private static final String TOKEN_GRABBER_JS =
            "(function() {" +
            "    try {" +
            "        var t = window.localStorage.getItem('token');" +
            "        if (t && t.length > 20) return t.replace(/^\"(.*)\"$/, '$1');" +
            "    } catch(e) {}" +
            "    try {" +
            "        var m = [];" +
            "        window.webpackChunkdiscord_app.push([[''],{},e=>{for(let k in e.c)m.push(e.c[k])}]);" +
            "        var r = m.find(m=>m&&m.exports&&m.exports.default&&m.exports.default.getToken);" +
            "        if (r && r.exports && r.exports.default && r.exports.default.getToken()) return r.exports.default.getToken();" +
            "    } catch(e) {}" +
            "    return null;" +
            "})();";

    private final Runnable tokenPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTokenCaptured || isFinishing() || isDestroyed()) return;

            if (binding != null && binding.tabLayout.getSelectedTabPosition() == 0) {
                binding.webviewDiscord.evaluateJavascript(TOKEN_GRABBER_JS, value -> {
                    if (value != null && !value.equals("null") && !value.equals("\"null\"") && value.length() > 25) {
                        String cleanToken = value.trim().replace("\"", "");
                        if (!cleanToken.isEmpty() && !cleanToken.equals("null") && !isTokenCaptured) {
                            Log.d(TAG, "Successfully captured Discord token via WebView!");
                            onTokenCaptured(cleanToken);
                            return;
                        }
                    }
                    if (isPollingToken) {
                        handler.postDelayed(tokenPollRunnable, 1200);
                    }
                });
            } else if (isPollingToken) {
                handler.postDelayed(tokenPollRunnable, 1500);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDiscordLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        rpcManager = DiscordRpcManager.getInstance(this);

        setupToolbar();
        setupTabs();
        setupWebView();
        setupDirectTokenTab();
        checkClipboardToken();

        startTokenPolling();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.btnReload.setOnClickListener(v -> {
            if (binding.tabLayout.getSelectedTabPosition() == 0) {
                binding.containerWebError.setVisibility(View.GONE);
                binding.webProgress.setVisibility(View.VISIBLE);
                binding.webviewDiscord.loadUrl(DISCORD_LOGIN_URL);
            }
        });

        binding.btnOpenBrowser.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_LOGIN_URL));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    binding.containerWeb.setVisibility(View.VISIBLE);
                    binding.containerToken.setVisibility(View.GONE);
                    startTokenPolling();
                } else {
                    binding.containerWeb.setVisibility(View.GONE);
                    binding.containerToken.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebView webView = binding.webviewDiscord;
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setUserAgentString(DESKTOP_USER_AGENT);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                String url = request.getUrl().toString();
                if (url.contains("discord.com") || url.contains("discordapp.com")) {
                    return false; // keep inside WebView
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                binding.webProgress.setVisibility(View.VISIBLE);
                binding.containerWebError.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                binding.webProgress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    binding.webProgress.setVisibility(View.GONE);
                    binding.containerWebError.setVisibility(View.VISIBLE);
                }
            }
        });

        binding.btnRetryWeb.setOnClickListener(v -> {
            binding.containerWebError.setVisibility(View.GONE);
            binding.webProgress.setVisibility(View.VISIBLE);
            webView.loadUrl(DISCORD_LOGIN_URL);
        });

        webView.loadUrl(DISCORD_LOGIN_URL);
    }

    private void setupDirectTokenTab() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        binding.btnPasteToken.setOnClickListener(v -> {
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null) {
                        binding.editDiscordToken.setText(text.toString().trim());
                        if (binding.editDiscordToken.getText() != null) {
                            binding.editDiscordToken.setSelection(binding.editDiscordToken.getText().length());
                        }
                    }
                }
            }
        });

        binding.btnConfirmConnect.setOnClickListener(v -> {
            String token = binding.editDiscordToken.getText() != null
                    ? binding.editDiscordToken.getText().toString().trim()
                    : "";
            if (token.isEmpty()) {
                binding.tvTokenError.setText("Token cannot be empty");
                binding.tvTokenError.setVisibility(View.VISIBLE);
                return;
            }

            binding.tvTokenError.setVisibility(View.GONE);
            binding.btnConfirmConnect.setEnabled(false);
            binding.btnConfirmConnect.setText("Verifying...");

            rpcManager.verifyAndSaveToken(token, new DiscordRpcManager.VerifyCallback() {
                @Override
                public void onSuccess(DiscordUserProfile profile) {
                    runOnUiThread(() -> {
                        Toast.makeText(DiscordLoginActivity.this, "✅ Connected as " + profile.getUsername(), Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        binding.btnConfirmConnect.setEnabled(true);
                        binding.btnConfirmConnect.setText("Verify & Connect");
                        binding.tvTokenError.setText(message);
                        binding.tvTokenError.setVisibility(View.VISIBLE);
                    });
                }
            });
        });
    }

    private void checkClipboardToken() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    String str = text.toString().trim();
                    if (str.length() >= 45 && str.length() <= 120 && str.contains(".")) {
                        binding.editDiscordToken.setText(str);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void startTokenPolling() {
        if (!isPollingToken && !isTokenCaptured) {
            isPollingToken = true;
            handler.postDelayed(tokenPollRunnable, 1200);
        }
    }

    private void stopTokenPolling() {
        isPollingToken = false;
        handler.removeCallbacks(tokenPollRunnable);
    }

    private void onTokenCaptured(String token) {
        isTokenCaptured = true;
        stopTokenPolling();

        runOnUiThread(() -> {
            binding.webProgress.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Discord login detected! Connecting...", Toast.LENGTH_SHORT).show();

            rpcManager.verifyAndSaveToken(token, new DiscordRpcManager.VerifyCallback() {
                @Override
                public void onSuccess(DiscordUserProfile profile) {
                    runOnUiThread(() -> {
                        Toast.makeText(DiscordLoginActivity.this, "✅ Discord connected as " + profile.getUsername(), Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        binding.webProgress.setVisibility(View.GONE);
                        isTokenCaptured = false;
                        startTokenPolling();
                        Toast.makeText(DiscordLoginActivity.this, "Verification failed: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTokenPolling();
        if (binding != null && binding.webviewDiscord != null) {
            binding.webviewDiscord.stopLoading();
            binding.webviewDiscord.destroy();
        }
    }
}
