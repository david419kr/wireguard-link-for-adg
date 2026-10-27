// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;

/** Shared editor: basic rename, optional raw config, validated atomic save. */
final class ProfileEditor {
    interface Saved { void saved(WireGuardProfile before, WireGuardProfile after); }

    static void show(Activity host, ProfileStore store, WireGuardProfile profile, Saved callback) {
        int gap = Math.round(16 * host.getResources().getDisplayMetrics().density);
        LinearLayout form = new LinearLayout(host); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(gap, gap / 2, gap, gap);
        TextView nameLabel = new TextView(host); nameLabel.setText(UiText.t(host, "이름")); form.addView(nameLabel);
        EditText name = new EditText(host); name.setSingleLine(true); name.setText(profile.name);
        name.setSelectAllOnFocus(true); name.setImeOptions(EditorInfo.IME_ACTION_DONE); form.addView(name);
        TextView advanced = new TextView(host); advanced.setText(UiText.t(host, "고급 (Advanced)") + " ▸");
        advanced.setTextSize(16); advanced.setTextColor(0xff76a645); advanced.setPadding(0, gap, 0, gap);
        advanced.setFocusable(true); form.addView(advanced);
        LinearLayout details = new LinearLayout(host); details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        TextView rawLabel = new TextView(host); rawLabel.setText(UiText.t(host, "원문 설정 (.conf)")); details.addView(rawLabel);
        EditText raw = new EditText(host);
        raw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        raw.setTypeface(Typeface.MONOSPACE); raw.setTextSize(12); raw.setText(profile.config);
        raw.setHorizontallyScrolling(true); raw.setVerticalScrollBarEnabled(true);
        raw.setMinLines(8); raw.setMaxLines(12);
        raw.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        if (Build.VERSION.SDK_INT >= 26) raw.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        details.addView(raw, new LinearLayout.LayoutParams(-1, -2)); form.addView(details);
        TextView error = new TextView(host); error.setTextColor(0xffef5350); error.setVisibility(View.GONE);
        form.addView(error);
        ScrollView scroll = new ScrollView(host); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(host).setTitle(UiText.t(host, "터널 편집"))
                .setView(scroll).setNegativeButton(UiText.t(host, "취소"), null)
                .setPositiveButton(UiText.t(host, "저장하기"), null).create();
        advanced.setOnClickListener(view -> {
            boolean expanded = details.getVisibility() != View.VISIBLE;
            details.setVisibility(expanded ? View.VISIBLE : View.GONE);
            advanced.setText(UiText.t(host, "고급 (Advanced)") + (expanded ? " ▾" : " ▸"));
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xff76a645);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xff76a645);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                WireGuardProfile updated;
                try { updated = store.update(profile.id, name.getText().toString(), raw.getText().toString()); }
                catch (IOException invalid) { error.setText(invalid.getMessage()); error.setVisibility(View.VISIBLE); return; }
                catch (RuntimeException unavailable) {
                    error.setText(UiText.t(host, "터널을 저장하지 못했습니다. 기존 설정은 유지됩니다."));
                    error.setVisibility(View.VISIBLE); return;
                }
                dialog.dismiss(); callback.saved(profile, updated);
            });
        });
        dialog.show();
    }
    private ProfileEditor() {}
}
