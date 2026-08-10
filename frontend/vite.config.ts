import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'

/**
 * Vite's SPA fallback rewrites any unknown path to the ROOT index.html, which
 * means a deep link into the admin console (/admin/users) would load the user
 * app instead — it renders, so the mistake is easy to miss.
 *
 * This sends anything under /admin that is not a file request to the admin
 * entry. The production equivalent is the rewrite in frontend/vercel.json.
 */
function adminSpaFallback(): Plugin {
  return {
    name: 'lifeos-admin-spa-fallback',
    configureServer(server) {
      server.middlewares.use((req, _res, next) => {
        const url = req.url ?? ''
        const path = url.split('?')[0]
        const isAsset = /\.[a-zA-Z0-9]+$/.test(path)
        if (path.startsWith('/admin') && !isAsset) {
          req.url = '/admin/index.html'
        }
        next()
      })
    },
    configurePreviewServer(server) {
      server.middlewares.use((req, _res, next) => {
        const path = (req.url ?? '').split('?')[0]
        const isAsset = /\.[a-zA-Z0-9]+$/.test(path)
        if (path.startsWith('/admin') && !isAsset) {
          req.url = '/admin/index.html'
        }
        next()
      })
    },
  }
}

// Two entry points, one codebase: / is the user app, /admin/ is the admin
// console. They share the theme and component library but nothing else — the
// admin bundle never ships the habit/expense screens and vice versa.
export default defineConfig({
  plugins: [react(), adminSpaFallback()],
  resolve: {
    alias: { '@': resolve(__dirname, 'src') },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        admin: resolve(__dirname, 'admin/index.html'),
      },
      output: {
        // Charts and the component library are big and change rarely; splitting
        // them keeps the app chunk small enough to stay cacheable across releases.
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-redux': ['@reduxjs/toolkit', 'react-redux'],
          'vendor-antd': ['antd', '@ant-design/icons'],
          'vendor-charts': ['recharts'],
          'vendor-motion': ['framer-motion'],
        },
      },
    },
  },
  server: {
    // Deliberately not 5173: the microservice edition of this app used that, and
    // running both at once while you migrate should not need a flag.
    port: 5273,
    host: true,
    proxy: {
      // Dev proxies to the single service, so the browser sees one origin and
      // CORS never enters the picture locally.
      '/api': {
        target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:9080',
        changeOrigin: true,
      },
    },
  },
  preview: { port: 4273, host: true },
})
