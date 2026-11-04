import { preview } from 'vite';

// 与部署时的同源网关一致，代理到真实后端，避免改变业务端跨域配置。
await preview({
  preview: {
    host: '127.0.0.1',
    port: 4173,
    strictPort: true,
    proxy: { '/v1': 'http://127.0.0.1:18765' },
  },
});
