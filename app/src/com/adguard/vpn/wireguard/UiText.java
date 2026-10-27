// Copyright 2026 david419kr
// SPDX-License-Identifier: Apache-2.0

package com.adguard.vpn.wireguard;

import android.content.Context;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

/** Korean UI with an English fallback; user-provided names/configuration are never translated. */
final class UiText {
    private static final Map<String,String> EN = new LinkedHashMap<>();
    static {
        EN.put(" 실패 (", " failed (");
        EN.put(" 터널 메뉴", " tunnel menu");
        EN.put(" 터널을 삭제할까요?", " — delete this tunnel?");
        EN.put(", 선택한 터널", ", selected tunnel");
        EN.put(", 터널 선택", ", select tunnel");
        EN.put(".conf 또는 WireGuard ZIP", ".conf or WireGuard ZIP");
        EN.put(".conf 또는 WireGuard 내보내기 ZIP", ".conf or WireGuard export ZIP");
        EN.put("AdG 대기 · 단독 VPN", "AdG idle · Standalone VPN");
        EN.put("AdG 미설치", "AdG is not installed");
        EN.put("AdG 앱 열기", "Open AdG");
        EN.put("AdG 앱이 설치되어 있지 않습니다.", "AdG is not installed.");
        EN.put("AdG 연결 대기", "Waiting for AdG");
        EN.put("AdG 연동 꺼짐", "AdG integration is off");
        EN.put("AdG 연동 상태 확인 중", "Checking AdG integration");
        EN.put("AdG 통합", "AdG integration");
        EN.put("AdG 통합 사용", "Use AdG integration");
        EN.put("AdGuard VPN 연결 중", "Connecting to AdGuard VPN");
        EN.put("AdGuard 서버 선택", "AdGuard server selection");
        EN.put("AdG를 열거나 연동을 꺼주세요", "Open AdG or turn off integration");
        EN.put("QR 카메라 읽기에는 카메라 권한이 필요합니다. 이미지 가져오기도 사용할 수 있습니다.", "Camera permission is required to scan QR codes. You can also import a QR image.");
        EN.put("QR 코드 이미지 가져오기", "Import QR code image");
        EN.put("QR 코드 읽기", "Scan QR code");
        EN.put("QR 터널", "QR tunnel");
        EN.put("UTF-8 설정 파일이 필요합니다.", "The configuration file must use UTF-8 encoding.");
        EN.put("VPN 권한 화면을 열 수 없습니다.", "Unable to open the VPN permission screen.");
        EN.put("VPN 권한이 필요합니다", "VPN permission required");
        EN.put("VPN 권한이 필요합니다 · 앱을 열어 주세요", "VPN permission required · Open the app");
        EN.put("VPN 시작", "VPN startup");
        EN.put("VPN 인터페이스", "VPN interface");
        EN.put("WireGuard QR 코드", "WireGuard QR code");
        EN.put("WireGuard QR 코드를 화면 안에 맞춰 주세요.", "Position the WireGuard QR code inside the camera view.");
        EN.put("WireGuard 설정 오류", "WireGuard configuration error");
        EN.put("WireGuard 연결", "WireGuard connection");
        EN.put("WireGuard 준비됨", "WireGuard ready");
        EN.put("WireGuard 터널", "WireGuard tunnels");
        EN.put("ZIP 내보내기", "Export ZIP");
        EN.put("ZIP 항목이 너무 많습니다.", "The ZIP contains too many entries.");
        EN.put("ZIP에서 .conf 터널을 찾지 못했습니다.", "No .conf tunnels were found in the ZIP.");
        EN.put("ZIP을 저장하지 못했습니다. 저장 공간과 파일 접근 권한을 확인해 주세요.", "Unable to save the ZIP. Check free space and file access permissions.");
        EN.put("ZIP을 저장하지 못했습니다. 저장 위치와 여유 공간을 확인해 주세요.", "Unable to save the ZIP. Check the destination and free space.");
        EN.put("ZIP의 압축 해제 크기가 너무 큽니다.", "The uncompressed ZIP contents are too large.");
        EN.put("가져오지 못했습니다. 올바른 WireGuard 설정·ZIP·QR인지 확인해 주세요. 기존 터널은 유지됩니다.", "Import failed. Check the WireGuard configuration, ZIP, or QR code. Existing tunnels are unchanged.");
        EN.put("가져오지 못했습니다. 올바른 WireGuard 설정·ZIP·QR인지, 파일 크기가 너무 크지 않은지 확인해 주세요. 기존 터널은 유지됩니다.", "Import failed. Check the WireGuard configuration, ZIP, or QR code and the file size. Existing tunnels are unchanged.");
        EN.put("가져올 데이터가 너무 큽니다.", "The import data is too large.");
        EN.put("가져올 파일이 너무 큽니다.", "The import file is too large.");
        EN.put("개", " tunnels");
        EN.put("개 터널을 가져왔습니다.", " tunnels imported.");
        EN.put("검색 결과가 없습니다", "No results found");
        EN.put("검색 결과가 없습니다.", "No results found.");
        EN.put("공식 VPN 화면", "Official VPN screen");
        EN.put("기존 터널 데이터가 보존되었습니다.", "Existing tunnel data has been preserved.");
        EN.put("내 터널", "My tunnels");
        EN.put("내보낼 ZIP이 너무 큽니다.", "The export ZIP is too large.");
        EN.put("내보낼 설정의 전체 크기가 너무 큽니다.", "The configurations are too large to export.");
        EN.put("내보낼 터널이 없습니다.", "There are no tunnels to export.");
        EN.put("내보낼 파일을 열 수 없습니다.", "Unable to open the export destination.");
        EN.put("다른 터널 이름으로 검색해 보세요.", "Try another tunnel name.");
        EN.put("독립 VPN", "Standalone VPN");
        EN.put("돌아가기", "Back");
        EN.put("라우팅 보호", "Routing protection");
        EN.put("먼저 터널을 선택해 주세요.", "Select a tunnel first.");
        EN.put("삭제", "Delete");
        EN.put("선택한 터널", "Selected tunnel");
        EN.put("설정 파일 크기가 올바르지 않습니다.", "The configuration file size is invalid.");
        EN.put("설정 파일이나 QR 코드로\n첫 터널을 추가해 보세요.", "Add your first tunnel using\na configuration file or QR code.");
        EN.put("설정 파일이나 QR 코드로 시작할 수 있어요.", "Start with a configuration file or QR code.");
        EN.put("설정이 비어 있습니다.", "The configuration is empty.");
        EN.put("알림 제목", "Notification title");
        EN.put("알림 표시", "Notification display");
        EN.put("암호화된 터널 저장소를 읽거나 저장할 수 없습니다. 기존 데이터는 보존됩니다.", "Unable to read or save the encrypted tunnel store. Existing data is preserved.");
        EN.put("암호화된 터널 저장소를 읽을 수 없습니다. 기존 데이터는 삭제하지 않았습니다.", "Unable to read the encrypted tunnel store. Existing data has not been deleted.");
        EN.put("앱을 열어 다시 연결해 주세요", "Open the app to reconnect");
        EN.put("연결", "Connect");
        EN.put("연결 상태 전달", "Connection state update");
        EN.put("연결 안 됨", "Not connected");
        EN.put("연결 전환 중", "Switching connection");
        EN.put("연결 종료", "Disconnect");
        EN.put("연결 종료 중", "Disconnecting");
        EN.put("연결 준비 중", "Preparing connection");
        EN.put("연결 중", "Connecting");
        EN.put("연결 해제", "Disconnect");
        EN.put("연결 해제 버튼 표시", "Disconnect button display");
        EN.put("연결 해제됨", "Disconnected");
        EN.put("연결됨", "Connected");
        EN.put("연결됨 · AdGuard 통합", "Connected · AdGuard integration");
        EN.put("연결됨 · WireGuard VPN", "Connected · WireGuard VPN");
        EN.put("연결이 중단되었습니다", "Connection interrupted");
        EN.put("연결하지 못했습니다 · 설정과 네트워크를 확인해 주세요", "Unable to connect · Check the configuration and network");
        EN.put("요청을 처리하지 못했습니다. VPN 상태와 앱 권한을 확인해 주세요.", "Unable to process the request. Check the VPN state and app permissions.");
        EN.put("유효한 WireGuard 설정이 아닙니다.", "This is not a valid WireGuard configuration.");
        EN.put("이미 가져온 터널입니다.", "This tunnel has already been imported.");
        EN.put("이미지를 읽을 수 없습니다.", "Unable to read the image.");
        EN.put("이미지에서 WireGuard QR 코드를 찾지 못했습니다.", "No WireGuard QR code was found in the image.");
        EN.put("재연결 중", "Reconnecting");
        EN.put("저장", "Saved");
        EN.put("저장 위치 선택", "Choose save location");
        EN.put("저장 위치 선택기를 열 수 없습니다.", "Unable to open the save location picker.");
        EN.put("전체 WireGuard 터널을 .conf 묶음으로 저장합니다. ZIP에는 VPN 비밀키가 포함되므로 안전한 위치에 보관해 주세요.", "Export all WireGuard tunnels as .conf files. The ZIP contains VPN private keys. Keep it in a safe location.");
        EN.put("전체 터널을 WireGuard 호환 .conf 파일로 저장합니다. ZIP에는 VPN 비밀키가 포함되므로 안전한 위치에 보관해 주세요.", "Export all tunnels as WireGuard-compatible .conf files. The ZIP contains VPN private keys. Keep it in a safe location.");
        EN.put("초기화", "Initialization");
        EN.put("최대 256개 터널까지 저장할 수 있습니다.", "You can store up to 256 tunnels.");
        EN.put("취소", "Cancel");
        EN.put("카메라로 QR 코드 읽기", "Scan QR code with camera");
        EN.put("카메라를 열 수 없습니다. 다른 앱의 카메라 사용을 끝내거나 QR 이미지를 가져와 주세요.", "Unable to open the camera. Close other apps using it, or import a QR image.");
        EN.put("탭하여 선택", "Tap to select");
        EN.put("터널 ZIP 내보내기", "Export tunnel ZIP");
        EN.put("터널 ZIP을 저장했습니다.", "Tunnel ZIP saved.");
        EN.put("터널 가져오기", "Import tunnels");
        EN.put("터널 검색", "Search tunnels");
        EN.put("터널 삭제", "Delete tunnel");
        EN.put("터널 삭제 요청을 처리하지 못했습니다.", "Unable to delete the tunnel.");
        EN.put("터널 선택", "Tunnel selection");
        EN.put("터널 선택 해제", "Clear tunnel selection");
        EN.put("터널 수가 너무 많습니다.", "There are too many tunnels.");
        EN.put("터널 저장소 사용 불가", "Tunnel store unavailable");
        EN.put("터널 저장소 오류", "Tunnel store error");
        EN.put("터널 저장소 읽기", "Read tunnel store");
        EN.put("터널 저장소를 열 수 없습니다. 저장된 데이터는 유지됩니다. AdGuard VPN으로 전환해 계속 사용할 수 있습니다.", "Unable to open the tunnel store. Saved data is preserved. You can switch to AdGuard VPN to continue.");
        EN.put("터널 준비", "Tunnel preparation");
        EN.put("터널을 가져오는 중…", "Importing tunnels…");
        EN.put("터널을 내보내는 중…", "Exporting tunnels…");
        EN.put("터널을 선택하세요", "Select a tunnel");
        EN.put("터널을 선택하지 못했습니다.", "Unable to select the tunnel.");
        EN.put("터널을 선택해 주세요", "Select a tunnel");
        EN.put("터널을 찾을 수 없습니다.", "Tunnel not found.");
        EN.put("터널을 추가해 보세요", "Add a tunnel");
        EN.put("파일 선택기를 열 수 없습니다.", "Unable to open the file picker.");
        EN.put("파일을 열 수 없습니다.", "Unable to open the file.");
        EN.put("프록시 시작", "Proxy startup");
        EN.put("현재 선택", "Current selection");
        EN.put("현재 선택 · WireGuard", "Current selection · WireGuard");
        EN.put("현재 터널: ", "Current tunnel: ");
        EN.put("확인", "OK");
        EN.put("＋  터널 가져오기", "＋  Import tunnels");
        EN.put("＋ 가져오기", "＋ Import");
        EN.put("%s 터널을 삭제할까요?", "Delete tunnel %s?");
        EN.put("%s 터널 메뉴", "%s tunnel menu");
        EN.put("%d개", "%d tunnels");
        EN.put("%d개 터널을 가져왔습니다.", "Imported %d tunnels.");
        EN.put("%s 실패 (%s)", "%s failed (%s)");
        EN.put("켜기", "Turn on");
        EN.put("끄기", "Turn off");
        EN.put("편집", "Edit");
        EN.put("터널 편집", "Edit tunnel");
        EN.put("이름", "Name");
        EN.put("고급 (Advanced)", "Advanced");
        EN.put("원문 설정 (.conf)", "Raw configuration (.conf)");
        EN.put("저장하기", "Save");
        EN.put("터널을 저장하지 못했습니다. 기존 설정은 유지됩니다.", "Unable to save the tunnel. Existing settings are unchanged.");
        EN.put("이름을 입력해 주세요.", "Enter a name.");
        EN.put("이름은 80자 이하로 입력해 주세요.", "Use a name of 80 characters or fewer.");
        EN.put("이름에 제어 문자를 사용할 수 없습니다.", "Control characters are not allowed in tunnel names.");
        EN.put("터널 수정", "Tunnel edit");
    }
    static Locale locale(Context context) {
        if (context != null) {
            android.os.LocaleList locales = context.getResources().getConfiguration().getLocales();
            if (!locales.isEmpty()) return locales.get(0);
        }
        return Locale.getDefault();
    }
    static String translate(String language, String key) {
        if (key == null) return "";
        return "ko".equals(language) ? key : EN.getOrDefault(key, key);
    }
    static String t(Context context, String key) { return translate(locale(context).getLanguage(), key); }
    static String t(String key) { return translate(Locale.getDefault().getLanguage(), key); }
    static String format(Context context, String key, Object... args) {
        return String.format(locale(context), t(context, key), args);
    }
    static Map<String,String> translations() { return Collections.unmodifiableMap(EN); }
    private UiText() {}
}
