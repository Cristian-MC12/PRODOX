# Carpeta de Imágenes

Esta carpeta contiene los recursos gráficos del proyecto PRODOX.

## Estructura recomendada:

```
images/
├── logos/          # Logos de la aplicación y empresa
├── icons/          # Iconos personalizados
├── backgrounds/    # Imágenes de fondo
└── avatars/        # Avatares predeterminados
```

## Uso en componentes:

```typescript
// En tu componente TypeScript
logoUrl = 'assets/images/logos/logo.png';
```

```html
<!-- En tu template HTML -->
<img src="assets/images/logos/logo.png" alt="Logo PRODOX">
```

## Formatos recomendados:
- **PNG** para logos e iconos con transparencia
- **SVG** para gráficos vectoriales (escalables)
- **WebP** o **JPG** para fotografías

## Optimización:
- Comprimir imágenes antes de subirlas
- Usar tamaños apropiados (no subir imágenes enormes)
