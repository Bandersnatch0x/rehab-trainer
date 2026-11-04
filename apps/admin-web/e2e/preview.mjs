import { preview } from 'vite';

// 端口可配置：本机若有残留进程占用默认端口，可用 E2E_PREVIEW_PORT / E2E_BACKEND_PORT 换端口。
const previewPort = Number(process.env.E2E_PREVIEW_PORT || 4173);
const backendPort = Number(process.env.E2E_BACKEND_PORT || 18765);

// 与部署时的同源网关一致，代理到真实后端，避免改变业务端跨域配置。
await preview({
  preview: {
    host: '127.0.0.1',
    port: previewPort,
    strictPort: true,
    proxy: { '/v1': `http://127.0.0.1:${backendPort}` },
  },
});
