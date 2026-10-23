# PRODOX V1 — Manual de Usuario (VitePress)

Manual interactivo de usuario para la plataforma **PRODOX V1**.

## Estructura
- `docs/`: Documentación canónica de las 10 etapas del ciclo de vida de proyectos ágiles.
- `docs/public/images/`: Capturas de pantalla reales de la interfaz de usuario.
- `docs/.vitepress/config.mjs`: Configuración de navegación, tema y metadatos.
- `AUDITORIA_FINAL_MANUAL_PRODOX_V1.md`: Informe técnico de auditoría y certificación de calidad.

## Comandos

```bash
# Instalar dependencias
npm install

# Modo desarrollo local (puerto 5173)
npm run docs:dev

# Compilar producción (genera docs/.vitepress/dist)
npm run docs:build

# Previsualizar build local (puerto 4173)
npm run docs:preview
```

## Despliegue en Vercel
1. En el panel de Vercel, selecciona **"Add New Project"** e importa este repositorio (`PRODOX`).
2. Configura:
   - **Framework Preset**: VitePress
   - **Root Directory**: `prodox-manual-vitepress`
   - **Build Command**: `npm run docs:build`
   - **Output Directory**: `docs/.vitepress/dist`
3. Haz clic en **Deploy**.
