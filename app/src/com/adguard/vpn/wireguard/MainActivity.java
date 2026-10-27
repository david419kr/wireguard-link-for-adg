// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent WireGuard UI; all controls operate the same selected tunnel. */
public final class MainActivity extends Activity {
    private static final int FILE = 0x5741, IMAGE = 0x5742, CAMERA = 0x5743, EXPORT = 0x5744;
    private static final int VPN = 0x5745, NOTIFICATIONS = 0x5746;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private ProfileStore store;
    private Shield focusShield;
    private LinearLayout rows;
    private TextView selected, state, mode, connect, count, integrationStatus, importButton, exportButton;
    private Switch integration;
    private boolean updating, busy, permissionRequest, notificationAsked, storageErrorShown;
    private Boolean paintedEnabled;
    private String query = "", rowSignature = "";
    private int pageColor, surface, foreground, secondary, outline;
    private final int accent = 0xff76a645, red = 0xffef5350;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            refresh();
            if (TunnelController.isEnabled() && !TunnelController.isConnected()
                    && TunnelController.needsVpnPermission(MainActivity.this)) requestVpn();
            main.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        boolean dark = (getResources().getConfiguration().uiMode & 0x30) == 0x20;
        pageColor = dark ? 0xff202020 : 0xfff4f6f8;
        surface = dark ? 0xff2b2d31 : Color.WHITE;
        foreground = dark ? 0xfff2f3f5 : 0xff202328;
        secondary = dark ? 0xffa4a7b0 : 0xff68717b;
        outline = dark ? 0xff41444c : 0xffd7dde3;
        getWindow().setStatusBarColor(pageColor);
        getWindow().setNavigationBarColor(pageColor);
        if (!dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        store = new ProfileStore(this);
        TunnelController.init(this);
        IntegrationManager.init(this);
        LinearLayout page = column();
        page.setBackgroundColor(pageColor);
        page.setPadding(dp(16), 0, dp(16), 0);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(16) + insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    dp(16) + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(page);

        EditText search = new EditText(this);
        search.setTextSize(16); search.setTextColor(foreground); search.setHintTextColor(secondary);
        search.setHint(UiText.t(this, "터널 검색")); search.setSingleLine(true); search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.setPadding(dp(16), 0, dp(16), 0); search.setBackground(rounded(surface, 12, outline));
        add(page, search, 16, dp(52));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int before, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                query = value.toString().trim().toLowerCase(Locale.ROOT); refreshRows();
            }
            public void afterTextChanged(Editable value) {}
        });

        LinearLayout focus = card();
        focus.setPadding(dp(20), dp(20), dp(20), dp(18));
        LinearLayout focusLine = row();
        focusShield = new Shield(false);
        focusLine.addView(focusShield, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout names = column();
        names.addView(label(UiText.t(this, "현재 선택 · WireGuard"), 12, secondary, false));
        selected = label(UiText.t(this, "터널을 선택하세요"), 20, foreground, true);
        selected.setMaxLines(2); selected.setEllipsize(TextUtils.TruncateAt.END); names.addView(selected);
        LinearLayout.LayoutParams nameSpace = new LinearLayout.LayoutParams(0, -2, 1);
        nameSpace.leftMargin = dp(14); focusLine.addView(names, nameSpace);
        focusLine.setMinimumHeight(dp(64)); focus.addView(focusLine);
        state = label(UiText.t(this, "연결 해제됨"), 14, secondary, false); add(focus, state, 12, -2);
        mode = label(UiText.t(this, "독립 VPN"), 12, secondary, false); add(focus, mode, 5, -2);
        add(page, focus, 12, -2);
        connect = action(UiText.t(this, "연결"), true, view -> toggleConnection());
        connect.setTextSize(17); add(page, connect, 14, dp(54));

        LinearLayout integrationRow = row();
        TextView integrationTitle = label(UiText.t(this, "AdG 통합"), 15, foreground, true);
        integrationTitle.setOnClickListener(view -> openAdGuard());
        integrationTitle.setContentDescription(UiText.t(this, "AdG 앱 열기")); integrationTitle.setFocusable(true);
        integrationRow.addView(integrationTitle, new LinearLayout.LayoutParams(0, -2, 1));
        integration = new Switch(this); integration.setContentDescription(UiText.t(this, "AdG 통합 사용"));
        integration.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{accent, secondary}));
        integration.setOnCheckedChangeListener((view, enabled) -> {
            if (updating) return;
            IntegrationManager.setEnabled(enabled); refresh();
            if (TunnelController.isEnabled() && TunnelController.needsVpnPermission(this)) requestVpn();
        });
        integrationRow.addView(integration); add(page, integrationRow, 14, dp(42));
        integrationStatus = label("", 12, secondary, false); add(page, integrationStatus, 0, -2);

        LinearLayout actions = row();
        importButton = action(UiText.t(this, "＋ 가져오기"), false, view -> chooseImport());
        exportButton = action(UiText.t(this, "ZIP 내보내기"), false, view -> chooseExport());
        actions.addView(importButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams exportSpace = new LinearLayout.LayoutParams(0, dp(46), 1);
        exportSpace.leftMargin = dp(10); actions.addView(exportButton, exportSpace);
        add(page, actions, 16, -2);
        LinearLayout section = row();
        section.addView(label(UiText.t(this, "내 터널"), 17, foreground, true), new LinearLayout.LayoutParams(0, -2, 1));
        count = label("", 13, secondary, false); section.addView(count); add(page, section, 20, -2);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        rows = column(); rows.setPadding(0, dp(12), 0, dp(20));
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        refresh();
    }

    @Override protected void onResume() {
        super.onResume(); CompatibilityShortcuts.refresh(this); main.removeCallbacks(poll); main.post(poll);
    }
    @Override protected void onPause() { main.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); files.shutdown(); super.onDestroy(); }

    private void refresh() {
        if (selected == null) return;
        try {
            String name = TunnelController.selectedName();
            selected.setText(name == null || name.isEmpty() ? UiText.t(this, "터널을 선택하세요") : name);
            state.setText(TunnelController.status());
            state.setTextColor(TunnelController.isConnected() ? accent : secondary);
            focusShield.setActive(ConnectionVisuals.active(TunnelController.isConnected(),
                    TunnelController.selectedId(), TunnelController.connectedId()));
            mode.setText(TunnelController.mode());
            boolean enabled = TunnelController.isEnabled();
            if (paintedEnabled == null || paintedEnabled != enabled) {
                paintedEnabled = enabled;
                connect.setText(enabled ? UiText.t(this, "연결 해제") : UiText.t(this, "연결"));
                connect.setTextColor(enabled ? foreground : 0xff14200d);
                connect.setBackground(enabled ? ripple(Color.TRANSPARENT, 10, red) : ripple(accent, 10, 0));
            }
            updating = true; integration.setChecked(IntegrationManager.isEnabled()); updating = false;
            integrationStatus.setText(IntegrationManager.status());
            refreshRows();
        } catch (Exception ignored) { showStorageError(); }
    }

    private void refreshRows() {
        if (rows == null) return;
        try {
            List<WireGuardProfile> profiles = store.list();
            String chosen = TunnelController.selectedId();
            boolean active = ConnectionVisuals.active(TunnelController.isConnected(), chosen, TunnelController.connectedId());
            StringBuilder signature = new StringBuilder(query).append('\n').append(chosen).append(':').append(active);
            for (WireGuardProfile profile : profiles) signature.append('\n').append(profile.id).append(':').append(profile.name);
            if (signature.toString().equals(rowSignature)) return;
            rowSignature = signature.toString(); rows.removeAllViews(); int visible = 0;
            for (WireGuardProfile profile : profiles) {
                if (!profile.name.toLowerCase(Locale.ROOT).contains(query)) continue;
                boolean current = profile.id.equals(chosen);
                LinearLayout card = card();
                card.setPadding(dp(16), dp(14), dp(10), dp(14));
                card.setBackground(ripple(surface, 18, current ? accent : 0));
                card.setFocusable(true); card.setSelected(current); buttonRole(card);
                card.setContentDescription(profile.name + (current ? UiText.t(this, ", 선택한 터널") : UiText.t(this, ", 터널 선택")));
                card.setOnClickListener(view -> {
                    try { TunnelController.select(profile.id); refresh(); }
                    catch (Exception ignored) { error(UiText.t(this, "터널을 선택하지 못했습니다.")); }
                });
                LinearLayout line = row(); line.addView(new Shield(current && active), new LinearLayout.LayoutParams(dp(40), dp(40)));
                LinearLayout text = column();
                TextView title = label(profile.name, 16, foreground, true);
                title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); text.addView(title);
                add(text, label(current ? UiText.t(this, "선택한 터널") : UiText.t(this, "탭하여 선택"), 12, current ? accent : secondary, false), 5, -2);
                LinearLayout.LayoutParams textSpace = new LinearLayout.LayoutParams(0, -2, 1);
                textSpace.leftMargin = dp(12); line.addView(text, textSpace);
                TextView more = label("⋮", 24, secondary, true); more.setGravity(Gravity.CENTER);
                more.setContentDescription(UiText.format(this, "%s 터널 메뉴", profile.name)); more.setFocusable(true); buttonRole(more);
                more.setOnClickListener(view -> {
                    PopupMenu menu = new PopupMenu(this, more);
                    menu.getMenu().add(UiText.t(this, "편집")).setOnMenuItemClickListener(item -> {
                        ProfileEditor.show(this, store, profile, (before, after) -> {
                            TunnelController.profileEdited(after.id); rowSignature = ""; refresh();
                        });
                        return true;
                    });
                    menu.getMenu().add(UiText.t(this, "삭제")).setOnMenuItemClickListener(item -> { confirmDelete(profile); return true; });
                    menu.show();
                });
                line.addView(more, new LinearLayout.LayoutParams(dp(40), dp(44))); card.addView(line);
                add(rows, card, visible++ == 0 ? 0 : 10, -2);
            }
            count.setText(UiText.format(this, "%d개", visible)); exportButton.setEnabled(!busy && !profiles.isEmpty());
            exportButton.setAlpha(exportButton.isEnabled() ? 1 : .45f);
            if (visible == 0) {
                TextView empty = label(profiles.isEmpty() ? UiText.t(this, "설정 파일이나 QR 코드로\n첫 터널을 추가해 보세요.") : UiText.t(this, "검색 결과가 없습니다."), 15, secondary, false);
                empty.setPadding(dp(20), dp(30), dp(20), dp(30)); empty.setGravity(Gravity.CENTER);
                add(rows, empty, 0, -2);
            }
        } catch (Exception ignored) { showStorageError(); }
    }

    private void toggleConnection() {
        if (TunnelController.isEnabled()) { TunnelController.setEnabled(this, false); refresh(); return; }
        if (TunnelController.selectedId() == null) { toast(UiText.t(this, "먼저 터널을 선택해 주세요.")); return; }
        if (Build.VERSION.SDK_INT >= 33 && !notificationAsked
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationAsked = true; permissionRequest = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATIONS); return;
        }
        enableWithPermission();
    }

    private void enableWithPermission() {
        if (TunnelController.needsVpnPermission(this)) { requestVpn(); return; }
        TunnelController.setEnabled(this, true); refresh();
    }
    private void requestVpn() {
        if (permissionRequest || isFinishing() || isDestroyed()) return;
        Intent request = VpnService.prepare(this);
        if (request == null) { TunnelController.setEnabled(this, true); return; }
        permissionRequest = true;
        try { startActivityForResult(request, VPN); }
        catch (Exception ignored) { permissionRequest = false; TunnelController.setEnabled(this, false); error(UiText.t(this, "VPN 권한 화면을 열 수 없습니다.")); }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == NOTIFICATIONS) { permissionRequest = false; enableWithPermission(); }
    }

    private void chooseImport() {
        if (busy) return;
        dialog(new AlertDialog.Builder(this).setTitle(UiText.t(this, "터널 가져오기"))
                .setItems(new String[]{UiText.t(this, ".conf 또는 WireGuard ZIP"), UiText.t(this, "카메라로 QR 코드 읽기"), UiText.t(this, "QR 코드 이미지 가져오기")}, (d, which) -> {
                    if (which == 1) startActivityForResult(new Intent(this, CameraScanActivity.class), CAMERA);
                    else {
                        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
                        open.setType(which == 2 ? "image/*" : "*/*");
                        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try { startActivityForResult(open, which == 2 ? IMAGE : FILE); }
                        catch (Exception ignored) { error(UiText.t(this, "파일 선택기를 열 수 없습니다.")); }
                    }
                }).setNegativeButton(UiText.t(this, "취소"), null));
    }
    private void chooseExport() {
        if (busy) return;
        dialog(new AlertDialog.Builder(this).setTitle(UiText.t(this, "터널 ZIP 내보내기"))
                .setMessage(UiText.t(this, "전체 터널을 WireGuard 호환 .conf 파일로 저장합니다. ZIP에는 VPN 비밀키가 포함되므로 안전한 위치에 보관해 주세요."))
                .setNegativeButton(UiText.t(this, "취소"), null).setPositiveButton(UiText.t(this, "저장 위치 선택"), (d, which) -> {
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/zip").putExtra(Intent.EXTRA_TITLE, "wireguard-export.zip");
                    try { startActivityForResult(save, EXPORT); }
                    catch (Exception ignored) { error(UiText.t(this, "저장 위치 선택기를 열 수 없습니다.")); }
                }));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == VPN) {
            permissionRequest = false;
            TunnelController.setEnabled(this, result == RESULT_OK); refresh(); return;
        }
        if (result != RESULT_OK || data == null || busy) return;
        if (request != FILE && request != IMAGE && request != CAMERA && request != EXPORT) return;
        Uri uri = data.getData(); String qr = request == CAMERA ? data.getStringExtra("wireguard_config") : null;
        if (request == CAMERA ? qr == null : uri == null) return;
        busy = true; importButton.setEnabled(false); exportButton.setEnabled(false);
        files.execute(() -> {
            String outcome;
            try {
                if (request == EXPORT) { store.exportZip(uri); outcome = UiText.t(this, "터널 ZIP을 저장했습니다."); }
                else {
                    List<WireGuardProfile> imported = request == CAMERA ? store.importText(UiText.t(this, "QR 터널"), qr) : store.importUri(uri, request == IMAGE);
                    outcome = imported.isEmpty() ? UiText.t(this, "이미 가져온 터널입니다.") : UiText.format(this, "%d개 터널을 가져왔습니다.", imported.size());
                }
            } catch (Exception ignored) {
                outcome = request == EXPORT ? UiText.t(this, "ZIP을 저장하지 못했습니다. 저장 공간과 파일 접근 권한을 확인해 주세요.") : UiText.t(this, "가져오지 못했습니다. 올바른 WireGuard 설정·ZIP·QR인지 확인해 주세요. 기존 터널은 유지됩니다.");
            }
            String message = outcome;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false; importButton.setEnabled(true); rowSignature = ""; refresh(); toast(message);
            });
        });
    }

    private void confirmDelete(WireGuardProfile profile) {
        dialog(new AlertDialog.Builder(this).setTitle(UiText.t(this, "터널 삭제")).setMessage(UiText.format(this, "%s 터널을 삭제할까요?", profile.name))
                .setNegativeButton(UiText.t(this, "취소"), null).setPositiveButton(UiText.t(this, "삭제"), (d, which) -> {
                    try {
                        if (profile.id.equals(TunnelController.selectedId())) {
                            TunnelController.setEnabled(this, false); store.delete(profile.id); TunnelController.select(null);
                        } else store.delete(profile.id);
                        rowSignature = ""; refresh();
                    } catch (Exception ignored) { error(UiText.t(this, "터널 삭제 요청을 처리하지 못했습니다.")); }
                }));
    }
    private void openAdGuard() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.adguard.android");
        if (launch == null) { toast(UiText.t(this, "AdG 앱이 설치되어 있지 않습니다.")); return; }
        startActivity(launch);
    }
    private void showStorageError() {
        if (!storageErrorShown) { storageErrorShown = true; error(UiText.t(this, "암호화된 터널 저장소를 읽을 수 없습니다. 기존 데이터는 삭제하지 않았습니다.")); }
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void error(String text) { if (!isFinishing() && !isDestroyed()) dialog(new AlertDialog.Builder(this).setTitle("WireGuard").setMessage(text).setPositiveButton(UiText.t(this, "확인"), null)); }
    private void dialog(AlertDialog.Builder builder) {
        AlertDialog shown = builder.show();
        for (int id : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE})
            if (shown.getButton(id) != null) shown.getButton(id).setTextColor(accent);
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private LinearLayout card() { LinearLayout view = column(); view.setBackground(rounded(surface, 18, 0)); return view; }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setIncludeFontPadding(false); view.setLineSpacing(dp(3), 1);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return view;
    }
    private TextView action(String title, boolean primary, View.OnClickListener listener) {
        TextView view = label(title, 15, primary ? 0xff14200d : foreground, true);
        view.setGravity(Gravity.CENTER); view.setPadding(dp(10), dp(8), dp(10), dp(8));
        view.setBackground(ripple(primary ? accent : Color.TRANSPARENT, 10, primary ? 0 : outline));
        view.setFocusable(true); buttonRole(view); view.setOnClickListener(listener); return view;
    }
    private GradientDrawable rounded(int fill, int radius, int border) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(radius));
        if (border != 0) shape.setStroke(dp(border == red ? 2 : 1), border); return shape;
    }
    private RippleDrawable ripple(int fill, int radius, int border) {
        return new RippleDrawable(ColorStateList.valueOf(border == red ? 0x40ef5350 : 0x4076a645),
                rounded(fill, radius, border), rounded(Color.WHITE, radius, 0));
    }
    private void add(LinearLayout parent, View child, int top, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, height); lp.topMargin = dp(top); parent.addView(child, lp);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void buttonRole(View view) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
            }
        });
    }
    private final class Shield extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;
        Shield(boolean value) {
            super(MainActivity.this);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            active = !value;
            setActive(value);
        }
        void setActive(boolean value) {
            if (active == value) return;
            active = value;
            int tint = active ? accent : secondary;
            setBackground(rounded((tint & 0x00ffffff) | 0x20000000, 12, 0));
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.translate(getWidth() / 2f, getHeight() / 2f); float scale = dp(24) / 24f; canvas.scale(scale, scale);
            paint.setColor(active ? accent : secondary); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeCap(Paint.Cap.ROUND);
            Path path = new Path(); path.moveTo(0, -10); path.lineTo(8, -7); path.lineTo(8, 0); path.cubicTo(8, 5, 4, 8, 0, 10); path.cubicTo(-4, 8, -8, 5, -8, 0); path.lineTo(-8, -7); path.close(); canvas.drawPath(path, paint);
            if (active) {
                path.reset(); path.moveTo(-4, 0); path.lineTo(-1, 3); path.lineTo(4, -3); canvas.drawPath(path, paint);
            }
            canvas.restore();
        }
    }
}
