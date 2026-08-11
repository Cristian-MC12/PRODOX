# SOLUCIÓN AL PROBLEMA DE LOMBOK EN MPDIA

**Problema:** Maven no está procesando las anotaciones de Lombok (@Getter, @Setter, @Slf4j)  
**Síntomas:** ~100 errores de compilación por métodos inexistentes  
**Afecta a:** Entidades y servicios PRE-EXISTENTES (no código de Fase 3)

---

## 🔍 DIAGNÓSTICO

### Entidades Afectadas
```
Factor.java
ProjectMember.java
MetricParametrizacion.java
MetricUsoRanking.java
ProjectInvitacion.java
Proyecto.java
AppUser.java
```

### Servicios Afectados
```
ProjectMemberService.java (~30 errores)
MetricRankingService.java (~50 errores)
CopilotoPlanService.java (~4 errores)
GeminiService.java (~6 errores por @Slf4j)
```

### Ejemplo de Error
```
[ERROR] cannot find symbol
  symbol:   method getName()
  location: variable factor of type com.mpdia.entity.Factor
```

Pero `Factor.java` SÍ tiene la anotación:
```java
@Entity
@Table(name = "factors")
@Getter @Setter @NoArgsConstructor
public class Factor {
    private String name;
    // ...
}
```

---

## 🛠️ SOLUCIONES PROPUESTAS

### SOLUCIÓN 1: Rebuild Completo del Proyecto (RECOMENDADO)

#### IntelliJ IDEA
```
1. Build > Clean Project
2. Build > Rebuild Project
3. Invalidate Caches: File > Invalidate Caches / Restart > Invalidate and Restart
4. Verificar annotation processing:
   - Settings > Build, Execution, Deployment > Compiler > Annotation Processors
   - ☑ Enable annotation processing
5. Reimportar proyecto Maven: Maven panel > Reload All Maven Projects
```

#### VSCode
```
1. Command Palette (Ctrl+Shift+P) > Java: Clean Java Language Server Workspace
2. Reiniciar VSCode
3. Reinstalar extensiones Java:
   - Extension Pack for Java
   - Lombok Annotations Support for VS Code
4. Asegurar que Lombok está en el classpath del proyecto
```

#### Línea de Comandos
```bash
cd mpdia-springboot

# Limpiar completamente
mvn clean

# Eliminar cache de Maven local (opcional pero recomendado)
rm -rf ~/.m2/repository/org/projectlombok

# Recompilar desde cero
mvn clean install -DskipTests

# Si sigue fallando, forzar descarga de dependencias
mvn dependency:purge-local-repository
mvn clean compile
```

---

### SOLUCIÓN 2: Configurar Maven Compiler Plugin

Agregar configuración explícita en `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <excludes>
                    <exclude>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                    </exclude>
                </excludes>
            </configuration>
        </plugin>
        
        <!-- AGREGAR ESTO -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>1.18.30</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Luego:
```bash
mvn clean compile
```

---

### SOLUCIÓN 3: Verificar Versión de Lombok

Actualizar Lombok a la última versión en `pom.xml`:

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version> <!-- Verificar última versión -->
    <optional>true</optional>
</dependency>
```

Verificar compatibilidad con Java 17:
- Lombok 1.18.20+ es compatible con Java 17
- Spring Boot 3.2.5 requiere Java 17+
- Asegurar versiones consistentes

---

### SOLUCIÓN 4: Generar Getters/Setters Manualmente (TEMPORAL)

Si las soluciones anteriores no funcionan, agregar getters/setters manualmente como solución temporal:

#### Factor.java
```java
@Entity
@Table(name = "factors")
// @Getter @Setter @NoArgsConstructor // Comentar Lombok temporalmente
@NoArgsConstructor
public class Factor {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private String category;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    
    // AGREGAR MANUALMENTE:
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

Repetir para las 7 entidades afectadas.

---

### SOLUCIÓN 5: Verificar Java Version y JAVA_HOME

```bash
# Verificar versión de Java
java -version

# Debe ser Java 17
# javac --version también debe ser 17

# Verificar JAVA_HOME
echo $JAVA_HOME  # Linux/Mac
echo %JAVA_HOME%  # Windows

# Maven debe usar la misma versión
mvn -v
```

Si hay inconsistencias:
1. Instalar Java 17 JDK
2. Configurar JAVA_HOME
3. Reiniciar terminal/IDE
4. Recompilar proyecto

---

## 🧪 VERIFICAR SOLUCIÓN

Después de aplicar cualquier solución:

```bash
cd mpdia-springboot

# Limpiar y compilar
mvn clean compile

# Si compila exitosamente, ejecutar tests
mvn test

# Iniciar aplicación
mvn spring-boot:run
```

**Señales de éxito:**
```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

**Si sigue fallando:**
```
[ERROR] cannot find symbol
  symbol:   method getName()
```
→ Probar siguiente solución

---

## 📋 CHECKLIST DE SOLUCIONES

Aplicar en orden:

- [ ] **Solución 1:** Rebuild completo en IDE
- [ ] **Solución 5:** Verificar Java 17 y JAVA_HOME
- [ ] **Solución 2:** Configurar maven-compiler-plugin
- [ ] **Solución 3:** Actualizar versión de Lombok
- [ ] **Solución 4:** Getters/setters manuales (última opción)

---

## 🎯 RESULTADO ESPERADO

Una vez solucionado el problema de Lombok:

✅ Proyecto compila sin errores  
✅ Tests pueden ejecutarse  
✅ Backend puede iniciar  
✅ **Fase 3 funciona completamente**  

---

## 💡 PREVENCIÓN FUTURA

Para evitar este problema en el futuro:

1. **Usar Lombok correctamente desde el inicio:**
   - Instalar plugin de Lombok en el IDE
   - Habilitar annotation processing
   - Verificar configuración antes de commit

2. **CI/CD:**
   - Agregar build en pipeline
   - Fallar si hay errores de compilación
   - No permitir merge sin build exitoso

3. **Documentar configuración:**
   - README con setup de IDE
   - Troubleshooting guide
   - Versiones requeridas (Java, Maven, Lombok)

4. **Alternativa a Lombok:**
   - Considerar usar Records de Java 17
   - O generar getters/setters con IDE
   - O usar bibliotecas alternativas

---

## 📞 SOPORTE

Si ninguna solución funciona:

1. **Verificar logs completos:**
   ```bash
   mvn clean compile -X > build.log 2>&1
   ```

2. **Buscar en el log:**
   - "annotation processor"
   - "lombok"
   - "cannot find symbol"

3. **Información útil para debug:**
   - Versión de Java: `java -version`
   - Versión de Maven: `mvn -v`
   - Sistema operativo
   - IDE y versión
   - Contenido completo de `pom.xml`

4. **Stack Overflow:**
   - Buscar: "maven lombok cannot find symbol"
   - Buscar: "lombok not generating getters setters maven"

---

**Autor:** Cristian Santiago Martinez Cordoba  
**Proyecto:** MPDIA  
**Fecha:** 10 de Agosto, 2026
