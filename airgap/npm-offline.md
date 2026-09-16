# npm 오프라인 반입

script-tag / JAR 사용 시 이 폴더는 불필요합니다. 번들러(`npm i livescreenlog`)로 SDK를 설치할 때만 필요합니다.

## 업로드

폐쇄망 Nexus npm-hosted에 `files/npm/*.tgz` 전부 publish 합니다.

또는 Verdaccio/Nexus에 일괄 업로드 후:

```bash
npm install livescreenlog@0.3.1 --registry https://nexus.internal/repository/npm-hosted/
```
