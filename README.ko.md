# WireGuard Link for AdG

**AdGuard 광고 차단 앱과 매끄럽게 연동·공존하는 WireGuard 클라이언트.**

AdGuard 없이도 독립적인 WireGuard VPN 클라이언트로 사용할 수 있습니다. 연동은 선택 사항이며 AdGuard VPN 계정이나 로그인은 필요하지 않습니다.

[English](README.md) · [소스 빌드](docs/BUILD.md) · [연동 규약](docs/INTEGRATION.md)

## 설치

[Releases](https://github.com/david419kr/wireguard-link-for-adg/releases)에서 APK를 받으세요.

**공식 AdGuard VPN 앱이 설치되어 있다면 먼저 삭제한 뒤 이 앱을 설치하세요. 두 앱은 동시에 설치할 수 없습니다.**

연동을 위해 패키지명 `com.adguard.vpn`을 사용합니다. 터널을 가져오고 선택한 뒤 연결 버튼을 누르세요. Android가 VPN·알림 권한을 요청할 수 있습니다.

## 주요 기능

- 단독 Android VPN 또는 AdGuard 광고 차단 앱과 인증된 로컬 프록시 연동
- `.conf`·WireGuard ZIP·카메라 QR·QR 이미지 가져오기, ZIP 내보내기
- 이름 변경 및 접힌 Advanced 영역에서 원문 설정 편집, 저장 전 검증
- 공통 연결/해제 버튼, 마지막 선택 기억, 연결 중 선택 터널 전환
- 터널명을 표시하는 퀵패널 타일과 길게 눌러 앱 열기
- 삼성 모드 및 루틴에서 사용하는 기존 토글 바로가기 식별자 호환
- 알림 연결/해제 버튼과 상태별 방패, ColorOS 등 앱 아이콘을 강제하는 시스템의 회색 방패 대응
- 한국어 및 그 외 언어의 영어 기본값

## AdGuard 연동

AdGuard를 함께 사용하려면 `AdG 통합`을 켜 두세요. 광고 차단 보호가 작동할 때는 AdGuard가 Android VPN 인터페이스를 사용하고 이 앱이 인증 로컬 프록시를 제공합니다. 연동을 끄면 이 앱 자체의 VPN을 사용합니다. 단독 모드 전환 시 Android VPN 승인이 필요할 수 있습니다.

AdGuard는 필수가 아닙니다. 단독 사용 시 `AdG 통합`을 끄면 됩니다. 버전 3 연동 규약을 구현했으며 Samsung SM-F968N과 AdGuard 4.15.0에서 상호 운용성을 확인했습니다. 모든 기기·OS의 동작을 보장하는 것은 아닙니다. [검증 범위](docs/TESTING.md)를 참고하세요.

## 개인정보와 라이선스

터널 설정은 Android Keystore로 기기 안에 암호화해 저장합니다. 광고·계정·분석·크래시 보고 SDK는 없습니다. 구성한 VPN/DNS 서버 연결과 VPN 통신을 수행하며, AdG 연동은 기기 내 Android IPC를 사용합니다.

직접 작성한 코드에 [Apache-2.0](LICENSE)을 적용합니다. 의존성의 원래 라이선스와 고지는 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)에 있습니다.

**AdGuard VPN 혹은 AdGuard의 어떤 리소스도 포함되지 않습니다.** 원본 앱의 코드·이미지·리소스·개조 APK·독점 VPN 엔진을 배포하지 않습니다. 패키지명·컴포넌트명 등은 호환성을 위한 식별자입니다.

비공식 독립 프로젝트이며 AdGuard 또는 WireGuard의 공식 제품이나 보증 제품이 아닙니다.
