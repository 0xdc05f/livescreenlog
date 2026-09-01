# `livescreenlog`

Browser SDK for [LiveScreenLog](https://github.com/0xdc05f/livescreenlog).

```bash
npm i livescreenlog
```

```ts
import { LiveScreenLog, SDK_VERSION } from 'livescreenlog';

console.log(SDK_VERSION); // e.g. 0.1.0

LiveScreenLog.init({
  apiKey: 'YOUR_PROJECT_KEY',
  dsn: 'https://your-livescreenlog-host',
  id: 'user-001',
});
```
Offline events are buffered (up to 2000) in localStorage with retry on network recovery.

## Script tag (no bundler)

```html
<script src="https://cdn.jsdelivr.net/npm/livescreenlog@0.1.1/dist/livescreenlog.js"></script>
<script>
  console.log(LiveScreenLog.version);
  LiveScreenLog.init({ apiKey: '...', dsn: '...', id: 'user-001' });
</script>
```

Or self-host from your server: `/livescreenlog.js`  
Or GitHub Release: https://github.com/0xdc05f/livescreenlog/releases

## Version

- `LiveScreenLog.version` / `SDK_VERSION`
- UMD file header: `/*! LiveScreenLog browser SDK vX.Y.Z ... */`
