// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ProfileStore {
    private static final Object LOCK = new Object();
    private static final String KEY_ALIAS = "adguard.wireguard.profiles.v1";
    private static final int MAX_CONFIG = 256 * 1024;
    private static final int MAX_ARCHIVE = 8 * 1024 * 1024;
    private static final int MAX_IMAGE = 32 * 1024 * 1024;
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_PROFILES = 256;
    private final Context context;
    private final AtomicFile file;

    public ProfileStore(Context context) {
        this.context = context.getApplicationContext();
        this.file = new AtomicFile(new File(this.context.getNoBackupFilesDir(), "wireguard-profiles.v1"));
    }

    public List<WireGuardProfile> list() {
        synchronized (LOCK) { return profiles(read()); }
    }

    public WireGuardProfile get(String id) {
        if (id == null) return null;
        for (WireGuardProfile profile : list()) if (profile.id.equals(id)) return profile;
        return null;
    }

    public String selectedId() {
        synchronized (LOCK) {
            String value = read().optString("selected", "");
            return value.isEmpty() ? null : value;
        }
    }

    public void select(String id) {
        synchronized (LOCK) {
            JSONObject state = read();
            if (id != null) {
                boolean found = false;
                for (WireGuardProfile profile : profiles(state)) if (profile.id.equals(id)) found = true;
                if (!found) throw new IllegalArgumentException(UiText.t("터널을 찾을 수 없습니다."));
            }
            try { state.put("selected", id == null ? "" : id); }
            catch (Exception ignored) { throw storageError(); }
            write(state);
        }
    }

    public List<WireGuardProfile> importText(String name, String config) throws IOException {
        if (config == null) throw new IOException(UiText.t("설정이 비어 있습니다."));
        return commit(Collections.singletonList(validate(name, config.getBytes(StandardCharsets.UTF_8))));
    }

    public List<WireGuardProfile> importUri(Uri uri, boolean qrImage) throws IOException {
        String name = "WireGuard";
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (Exception ignored) { }
        byte[] bytes;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException(UiText.t("파일을 열 수 없습니다."));
            bytes = readBounded(stream, qrImage ? MAX_IMAGE : MAX_ARCHIVE);
        }
        if (qrImage) return importText(name, decodeImage(bytes));
        return commit(decodeImport(bytes, name));
    }

    public void delete(String id) {
        synchronized (LOCK) {
            JSONObject state = read();
            JSONArray kept = new JSONArray();
            for (WireGuardProfile profile : profiles(state)) if (!profile.id.equals(id)) kept.put(json(profile));
            try {
                state.put("profiles", kept);
                if (id != null && id.equals(state.optString("selected"))) state.put("selected", "");
            } catch (Exception ignored) { throw storageError(); }
            write(state);
        }
    }

    public WireGuardProfile update(String id, String name, String config) throws IOException {
        synchronized (LOCK) {
            JSONObject state = read();
            List<WireGuardProfile> current = profiles(state);
            WireGuardProfile changed = null;
            JSONArray replacement = new JSONArray();
            for (WireGuardProfile profile : current) {
                if (profile.id.equals(id)) {
                    changed = edited(profile, name, config);
                    replacement.put(json(changed));
                } else replacement.put(json(profile));
            }
            if (changed == null) throw new IOException(UiText.t("터널을 찾을 수 없습니다."));
            try { state.put("profiles", replacement); }
            catch (Exception invalid) { throw storageError(); }
            write(state);
            return changed;
        }
    }

    static WireGuardProfile edited(WireGuardProfile previous, String name, String config) throws IOException {
        name = name == null ? "" : name.trim();
        if (name.isEmpty()) throw new IOException(UiText.t("이름을 입력해 주세요."));
        if (name.length() > 80) throw new IOException(UiText.t("이름은 80자 이하로 입력해 주세요."));
        if (name.matches("(?s).*[\\p{Cntrl}].*")) throw new IOException(UiText.t("이름에 제어 문자를 사용할 수 없습니다."));
        if (config == null || config.isEmpty()) throw new IOException(UiText.t("설정이 비어 있습니다."));
        if (config.length() > MAX_CONFIG || config.getBytes(StandardCharsets.UTF_8).length > MAX_CONFIG)
            throw new IOException(UiText.t("설정 파일 크기가 올바르지 않습니다."));
        if (config.startsWith("\uFEFF")) config = config.substring(1);
        WireGuardProfile result = new WireGuardProfile(previous.id, name, config);
        result.parsed();
        return result;
    }

    public void exportZip(Uri destination) throws IOException {
        byte[] archive = encodeExport(list());
        try (OutputStream output = context.getContentResolver().openOutputStream(destination, "wt")) {
            if (output == null) throw new IOException(UiText.t("내보낼 파일을 열 수 없습니다."));
            output.write(archive);
        }
    }

    static byte[] encodeExport(List<WireGuardProfile> profiles) throws IOException {
        if (profiles.isEmpty()) throw new IOException(UiText.t("내보낼 터널이 없습니다."));
        if (profiles.size() > MAX_PROFILES) throw new IOException(UiText.t("터널 수가 너무 많습니다."));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Set<String> names = new HashSet<>();
        int total = 0;
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (WireGuardProfile profile : profiles) {
                byte[] config = profile.parsed().toWgQuickString().getBytes(StandardCharsets.UTF_8);
                total += config.length;
                if (config.length > MAX_CONFIG || total > MAX_ARCHIVE)
                    throw new IOException(UiText.t("내보낼 설정의 전체 크기가 너무 큽니다."));
                String stem = cleanName(profile.name).replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("[. ]+$", "");
                if (stem.isEmpty()) stem = "WireGuard";
                String name = stem + ".conf";
                int suffix = 2;
                while (!names.add(name.toLowerCase(Locale.ROOT))) name = stem + " (" + suffix++ + ").conf";
                zip.putNextEntry(new ZipEntry(name));
                zip.write(config);
                zip.closeEntry();
            }
        }
        if (bytes.size() > MAX_ARCHIVE) throw new IOException(UiText.t("내보낼 ZIP이 너무 큽니다."));
        return bytes.toByteArray();
    }

    // Package-visible so malformed/oversized ZIP imports are tested without device access.
    static List<WireGuardProfile> decodeImport(byte[] bytes, String name) throws IOException {
        if (bytes.length > MAX_ARCHIVE) throw new IOException(UiText.t("가져올 파일이 너무 큽니다."));
        List<WireGuardProfile> result = new ArrayList<>();
        boolean zipName = name != null && name.toLowerCase(Locale.ROOT).endsWith(".zip");
        boolean zipHeader = bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K';
        if (zipName || zipHeader) {
            int entries = 0;
            int total = 0;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) throw new IOException(UiText.t("ZIP 항목이 너무 많습니다."));
                    byte[] data = readBounded(zip, MAX_CONFIG);
                    total += data.length;
                    if (total > MAX_ARCHIVE) throw new IOException(UiText.t("ZIP의 압축 해제 크기가 너무 큽니다."));
                    if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".conf")) {
                        result.add(validate(entry.getName(), data));
                    }
                    zip.closeEntry();
                }
            }
            if (result.isEmpty()) throw new IOException(UiText.t("ZIP에서 .conf 터널을 찾지 못했습니다."));
        } else result.add(validate(name, bytes));
        return result;
    }

    private static WireGuardProfile validate(String name, byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_CONFIG) throw new IOException(UiText.t("설정 파일 크기가 올바르지 않습니다."));
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception ignored) { throw new IOException(UiText.t("UTF-8 설정 파일이 필요합니다.")); }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        WireGuardProfile candidate = new WireGuardProfile(UUID.randomUUID().toString(), cleanName(name), text);
        return new WireGuardProfile(candidate.id, candidate.name, candidate.parsed().toWgQuickString());
    }

    private static String cleanName(String name) {
        if (name == null) name = "WireGuard";
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("(?i)\\.(conf|png|jpe?g|webp)$", "");
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        return name.isEmpty() ? "WireGuard" : name.substring(0, Math.min(name.length(), 80));
    }

    private List<WireGuardProfile> commit(List<WireGuardProfile> incoming) throws IOException {
        synchronized (LOCK) {
            JSONObject state = read();
            List<WireGuardProfile> existing = profiles(state);
            List<WireGuardProfile> added = new ArrayList<>();
            for (WireGuardProfile candidate : incoming) {
                boolean duplicate = false;
                for (WireGuardProfile prior : existing) if (prior.config.equals(candidate.config)) duplicate = true;
                if (duplicate) continue;
                if (existing.size() >= MAX_PROFILES) throw new IOException(UiText.t("최대 256개 터널까지 저장할 수 있습니다."));
                String name = candidate.name;
                int suffix = 2;
                while (containsName(existing, name)) name = candidate.name + " (" + suffix++ + ")";
                WireGuardProfile unique = new WireGuardProfile(candidate.id, name, candidate.config);
                existing.add(unique);
                added.add(unique);
            }
            JSONArray array = new JSONArray();
            for (WireGuardProfile profile : existing) array.put(json(profile));
            try { state.put("profiles", array); }
            catch (Exception ignored) { throw storageError(); }
            if (!added.isEmpty()) write(state);
            return added;
        }
    }

    private static boolean containsName(List<WireGuardProfile> profiles, String name) {
        for (WireGuardProfile profile : profiles) if (profile.name.equals(name)) return true;
        return false;
    }

    private static JSONObject json(WireGuardProfile profile) {
        try {
            return new JSONObject().put("id", profile.id).put("name", profile.name).put("config", profile.config);
        } catch (Exception ignored) { throw storageError(); }
    }

    private static List<WireGuardProfile> profiles(JSONObject state) {
        List<WireGuardProfile> result = new ArrayList<>();
        JSONArray array = state.optJSONArray("profiles");
        if (array == null) return result;
        try {
            if (array.length() > MAX_PROFILES) throw storageError();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new WireGuardProfile(item.getString("id"), item.getString("name"), item.getString("config")));
            }
            return result;
        } catch (Exception ignored) { throw storageError(); }
    }

    private JSONObject read() {
        if (!file.getBaseFile().exists()
                && !new File(file.getBaseFile().getPath() + ".bak").exists()) return new JSONObject();
        try (InputStream input = file.openRead()) {
            byte[] encrypted = readBounded(input, 80 * 1024 * 1024);
            if (encrypted.length < 29 || encrypted[0] != 1) throw storageError();
            byte[] iv = new byte[12];
            System.arraycopy(encrypted, 1, iv, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new JSONObject(new String(cipher.doFinal(encrypted, 13, encrypted.length - 13), StandardCharsets.UTF_8));
        } catch (Exception ignored) { throw storageError(); }
    }

    private void write(JSONObject state) {
        FileOutputStream output = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            if (cipher.getIV().length != 12) throw storageError();
            byte[] encrypted = cipher.doFinal(state.toString().getBytes(StandardCharsets.UTF_8));
            output = file.startWrite();
            output.write(1);
            output.write(cipher.getIV());
            output.write(encrypted);
            file.finishWrite(output);
        } catch (Exception ignored) {
            if (output != null) file.failWrite(output);
            throw storageError();
        }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    private static IllegalStateException storageError() {
        return new IllegalStateException(UiText.t("암호화된 터널 저장소를 읽거나 저장할 수 없습니다. 기존 데이터는 보존됩니다."));
    }

    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() > limit - count) throw new IOException(UiText.t("가져올 데이터가 너무 큽니다."));
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String decodeImage(byte[] bytes) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth < 1 || options.outHeight < 1) throw new IOException(UiText.t("이미지를 읽을 수 없습니다."));
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException(UiText.t("이미지를 읽을 수 없습니다."));
        try {
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            MultiFormatReader reader = new MultiFormatReader();
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            try { return reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText(); }
            catch (Exception ignored) {
                try { return reader.decode(new BinaryBitmap(new HybridBinarizer(source.invert())), hints).getText(); }
                catch (Exception alsoIgnored) { throw new IOException(UiText.t("이미지에서 WireGuard QR 코드를 찾지 못했습니다.")); }
            }
        } finally { bitmap.recycle(); }
    }
}
