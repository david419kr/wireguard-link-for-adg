// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

public final class UiTextTest {
    public static void main(String[] args) {
        Locale saved = Locale.getDefault();
        int checks = 0;
        try {
            Locale.setDefault(Locale.ENGLISH);
            for (Map.Entry<String,String> entry : UiText.translations().entrySet()) {
                String ko = entry.getKey(), en = entry.getValue();
                require(!en.isEmpty() && !Pattern.compile("[가-힣]").matcher(en).find());
                require(UiText.t(ko).equals(en));
                require(UiText.translate("ko", ko).equals(ko));
                require(UiText.translate("de", ko).equals(en));
                List<Object> parameters = new ArrayList<>();
                Matcher formats = Pattern.compile("%([sd])").matcher(ko);
                while (formats.find()) parameters.add("d".equals(formats.group(1)) ? 3 : "Home");
                String formatted = UiText.format(null, ko, parameters.toArray());
                require(!Pattern.compile("[가-힣]").matcher(formatted).find());
                checks += 5;
            }
            Locale.setDefault(Locale.KOREAN);
            require(UiText.t("취소").equals("취소"));
            Locale.setDefault(Locale.ENGLISH);
            require(UiText.t("취소").equals("Cancel"));
            require(UiText.translate("en", "My custom tunnel").equals("My custom tunnel"));
            checks += 3;
        } finally { Locale.setDefault(saved); }
        System.out.println("Korean/English localization checks passed: " + checks);
    }
    private static void require(boolean value) { if (!value) throw new AssertionError("Missing or invalid UI translation"); }
}
