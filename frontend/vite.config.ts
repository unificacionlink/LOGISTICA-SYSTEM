import { readFileSync } from 'node:fs'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// La version visible de la app es la primera entrada de src/novedades.ts. Se lee
// aqui para que el pie la muestre sin descargar todo el historial al entrar.
const novedades = readFileSync(new URL('./src/novedades.ts', import.meta.url), 'utf8')
const version = /version:\s*"([^"]+)"/.exec(novedades)?.[1]
if (!version) throw new Error('No se encontro la version actual en src/novedades.ts')

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: { __VERSION_APP__: JSON.stringify(version) },
})
