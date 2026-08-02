# 🔒 Seguridad y Configuración

## ⚠️ IMPORTANTE: API Key Filtrada

Si estás viendo este mensaje, es porque se detectó una API key filtrada en el repositorio público.

### Pasos para solucionar:

1. **Revoca la API key comprometida** inmediatamente en:
   - Google AI Studio: https://aistudio.google.com/app/apikey
   
2. **Genera una nueva API key** y guárdala de forma segura

3. **Configura tu entorno local:**
   ```bash
   cd mpdia-springboot/src/main/resources
   cp application.properties.example application.properties
   # Edita application.properties y agrega tu nueva API key
   ```

4. **Nunca subas** el archivo `application.properties` a Git (ya está en `.gitignore`)

## 🛡️ Mejores Prácticas

### Archivos que NUNCA deben estar en Git:
- `application.properties` (contiene secretos)
- Archivos `.env`
- Cualquier archivo con API keys, passwords, tokens

### Archivos que SÍ deben estar en Git:
- `application.properties.example` (plantilla sin secretos)
- `.gitignore` (lista de archivos a ignorar)

## 🔄 Limpieza del Historial de Git

Si ya subiste secretos a Git, necesitas limpiar el historial:

```bash
# OPCIÓN 1: Usar git-filter-repo (recomendado)
pip install git-filter-repo
git filter-repo --path mpdia-springboot/src/main/resources/application.properties --invert-paths

# OPCIÓN 2: Resetear el repositorio (pérdida de historial)
# Solo si es aceptable perder el historial
rm -rf .git
git init
git add .
git commit -m "Initial commit (cleaned)"
git remote add origin https://github.com/Cristian-MC12/MPDIA.git
git push -f origin main
```

## 📋 Checklist de Seguridad

- [ ] Revocada API key comprometida
- [ ] Generada nueva API key
- [ ] Actualizado `.gitignore`
- [ ] Creado `application.properties` local (no en Git)
- [ ] Limpiado historial de Git
- [ ] Push forzado al repositorio

## 📞 Contacto

Si necesitas ayuda, contacta al equipo de desarrollo.
