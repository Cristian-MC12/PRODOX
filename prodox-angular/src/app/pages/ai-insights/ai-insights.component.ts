// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AIInsightsService } from '../../services/ai-insights.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { AIInsight, InsightEvidence } from '../../models/ai-insights.model';
import { LimpiarMarkdownIAPipe } from '../../core/limpiar-markdown-ia.pipe';

@Component({
  selector: 'app-ai-insights',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, LimpiarMarkdownIAPipe],
  templateUrl: './ai-insights.component.html',
  styleUrl: './ai-insights.component.css'
})
export class AIInsightsComponent implements OnInit {

  proyecto: ProyectoDto | null = null;
  insights: AIInsight[] = [];
  
  loading = signal(false);
  generating = signal(false);
  alertMsg = signal('');
  alertClass = signal('alert-info');
  generationStep = signal('');
  
  // IN.5 — Priorización
  sortBy = signal<'fecha' | 'severidad'>('severidad');
  sortDirection = signal<'asc' | 'desc'>('desc');
  typeFilter = signal<string>('ALL');

  // Estado de edición
  editingInsightId: string | null = null;
  editedTitle: string = '';
  editedDescription: string = '';
  editedRecommendation: string = '';
  originalInsight: AIInsight | null = null;

  constructor(
    public router: Router,
    private insightsService: AIInsightsService
  ) {}

  ngOnInit(): void {
    const raw = localStorage.getItem('mpdia_proyecto_activo');
    this.proyecto = raw ? JSON.parse(raw) : null;
    
    if (this.proyecto) {
      this.loadInsights();
    }
  }

  loadInsights(): void {
    if (!this.proyecto) return;

    this.loading.set(true);
    this.alertMsg.set('');

    this.insightsService.getProjectInsights(this.proyecto.id)
      .pipe(
        catchError(err => {
          console.error('Error cargando insights:', err);
          this.showAlert('Error al cargar insights', 'alert-danger');
          return of([]);
        })
      )
      .subscribe(data => {
        this.insights = data;
        this.loading.set(false);
      });
  }

  generateInsights(): void {
    if (!this.proyecto || this.generating()) return;

    this.generating.set(true);
    this.updateGenerationProgress('Preparando análisis...');
    let hadError = false;

    // Simular pasos de generación para mejor UX
    setTimeout(() => this.updateGenerationProgress('Analizando métricas del proyecto...'), 1000);
    setTimeout(() => this.updateGenerationProgress('Detectando patrones y anomalías...'), 3000);
    setTimeout(() => this.updateGenerationProgress('Generando insights con IA...'), 5000);

    this.insightsService.generateInsights(this.proyecto.id)
      .pipe(
        catchError(err => {
          console.error('Error generando insights:', err);
          hadError = true;
          this.generationStep.set('');

          if (err.status === 403) {
            this.showAlert('No tienes permisos para generar insights en este proyecto', 'alert-danger');
          } else {
            this.showAlert('Error al generar insights. Intentá nuevamente.', 'alert-danger');
          }

          return of(null);
        })
      )
      .subscribe(resultado => {
        if (!hadError && resultado) {
          this.updateGenerationProgress('Finalizando...');
          setTimeout(() => {
            this.generating.set(false);
            // Recarga la lista completa (persistida) en vez de reemplazarla
            // solo con la tanda nueva, para que se vea el estado real acumulado.
            // IMPORTANTE: loadInsights() limpia alertMsg al iniciar, así que
            // debe llamarse ANTES de mostrar el mensaje de estado de esta
            // generación — si se llamara después, borraría el mensaje recién
            // mostrado apenas se disparara.
            this.loadInsights();
            // FASE 23: antes de esto la respuesta era solo AIInsight[] y un
            // fallo parcial de Gemini se veía exactamente igual que una
            // corrida completa con pocos resultados. Ahora el backend
            // distingue el estado real de la corrida (ver
            // GenerateInsightsResultDto) y se lo comunicamos al usuario.
            switch (resultado.status) {
              case 'SIN_DATOS':
                this.showAlert('No se generaron insights. El proyecto todavía no tiene sprints finalizados.', 'alert-warning');
                break;
              case 'SIN_SENALES':
                this.showAlert('No se generaron insights. El proyecto puede no tener suficientes datos históricos o señales significativas.', 'alert-warning');
                break;
              case 'FAILED':
                this.showAlert('No se pudo generar ningún insight: el servicio de IA no respondió correctamente. Intentá nuevamente en unos segundos.', 'alert-danger');
                break;
              case 'PARTIAL':
                this.showAlert(
                  `Se generaron ${resultado.senalesNuevas} insight(s), pero ${resultado.errores.length} señal(es) no pudieron procesarse (el servicio de IA no respondió para todas). Podés reintentar más tarde.`,
                  'alert-warning');
                break;
              default: { // COMPLETE
                const nuevosTexto = resultado.senalesNuevas > 0
                  ? `${resultado.senalesNuevas} insight(s) nuevo(s) generado(s)`
                  : 'Sin novedades';
                const omitidosTexto = resultado.senalesOmitidasPorDuplicado > 0
                  ? ` (${resultado.senalesOmitidasPorDuplicado} señal(es) ya estaban cubiertas, no se duplicaron)`
                  : '';
                this.showAlert(`${nuevosTexto}${omitidosTexto}`, 'alert-success');
              }
            }
          }, 500);
        } else {
          this.generating.set(false);
        }
      });
  }

  dismissInsight(insight: AIInsight): void {
    if (!confirm('¿Estás seguro de que querés descartar este insight?')) {
      return;
    }

    let hadError = false;

    this.insightsService.dismissInsight(insight.id)
      .pipe(
        catchError(err => {
          console.error('Error descartando insight:', err);
          hadError = true;
          this.showAlert('Error al descartar el insight', 'alert-danger');
          return of(null);
        })
      )
      .subscribe(() => {
        if (!hadError) {
          this.showAlert('Insight descartado', 'alert-success');
          this.insights = this.insights.filter(i => i.id !== insight.id);
        }
      });
  }

  getSeverityBadgeClass(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'bg-danger';
      case 'HIGH': return 'bg-warning';
      case 'MEDIUM': return 'bg-info';
      case 'LOW': return 'bg-secondary';
      default: return 'bg-secondary';
    }
  }

  getSeverityLabel(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'Crítico';
      case 'HIGH': return 'Alto';
      case 'MEDIUM': return 'Medio';
      case 'LOW': return 'Bajo';
      default: return severity;
    }
  }

  getTypeIcon(type: string): string {
    switch (type) {
      case 'TREND': return 'bi-graph-up-arrow';
      case 'ANOMALY': return 'bi-exclamation-triangle';
      case 'RISK': return 'bi-shield-exclamation';
      case 'COMPARISON': return 'bi-arrow-left-right';
      default: return 'bi-info-circle';
    }
  }

  getTypeLabel(type: string): string {
    switch (type) {
      case 'TREND': return 'Tendencia';
      case 'ANOMALY': return 'Anomalía';
      case 'RISK': return 'Riesgo';
      case 'COMPARISON': return 'Comparación';
      default: return type;
    }
  }

  getConfidenceBadgeClass(confidence: string): string {
    switch (confidence) {
      case 'HIGH': return 'badge-success';
      case 'MEDIUM': return 'badge-warning';
      case 'LOW': return 'badge-secondary';
      default: return 'badge-secondary';
    }
  }

  getConfidenceLabel(confidence: string): string {
    switch (confidence) {
      case 'HIGH': return 'Alta';
      case 'MEDIUM': return 'Media';
      case 'LOW': return 'Baja';
      default: return confidence;
    }
  }

  formatEvidence(evidence: InsightEvidence[]): string {
    if (!evidence || evidence.length === 0) return 'Sin datos de evidencia';

    return evidence.map(e => {
      const parts: string[] = [];
      parts.push(`Categoría: ${e.categoria}`);
      
      if (e.valorActual !== null) {
        parts.push(`Valor actual: ${e.valorActual.toFixed(2)}`);
      }
      if (e.valorAnterior !== null) {
        parts.push(`Valor anterior: ${e.valorAnterior.toFixed(2)}`);
      }
      if (e.promedioHistorico !== null) {
        parts.push(`Promedio histórico: ${e.promedioHistorico.toFixed(2)}`);
      }
      if (e.variacionPorcentual !== null) {
        parts.push(`Variación: ${e.variacionPorcentual.toFixed(1)}%`);
      }
      if (e.tendencia) {
        parts.push(`Tendencia: ${e.tendencia}`);
      }
      if (e.numeroSprints !== null) {
        parts.push(`Sprints analizados: ${e.numeroSprints}`);
      }
      
      return parts.join(' | ');
    }).join('\n');
  }

  private showAlert(message: string, cssClass: string): void {
    this.alertMsg.set(message);
    this.alertClass.set(cssClass);
    setTimeout(() => this.alertMsg.set(''), 5000);
  }

  private updateGenerationProgress(step: string): void {
    this.generationStep.set(step);
    this.showAlert(step, 'alert-info');
  }

  /**
   * IN.5 - Filtra y ordena insights según criterios actuales.
   */
  get filteredInsights(): AIInsight[] {
    let filtered = [...this.insights];

    // Filtrar por tipo si está seleccionado
    if (this.typeFilter() !== 'ALL') {
      filtered = filtered.filter(insight => insight.type === this.typeFilter());
    }

    // Ordenar
    filtered.sort((a, b) => {
      let comparison = 0;

      if (this.sortBy() === 'fecha') {
        const dateA = new Date(a.createdAt).getTime();
        const dateB = new Date(b.createdAt).getTime();
        comparison = dateA - dateB;
      } else if (this.sortBy() === 'severidad') {
        // Severidad: CRITICAL > HIGH > MEDIUM > LOW
        const severityOrder = { 'CRITICAL': 4, 'HIGH': 3, 'MEDIUM': 2, 'LOW': 1 };
        const severityA = severityOrder[a.severity as keyof typeof severityOrder] || 0;
        const severityB = severityOrder[b.severity as keyof typeof severityOrder] || 0;
        comparison = severityA - severityB;
      }

      return this.sortDirection() === 'asc' ? comparison : -comparison;
    });

    return filtered;
  }

  toggleSortDirection(): void {
    this.sortDirection.update(dir => dir === 'asc' ? 'desc' : 'asc');
  }

  // ============ EDICIÓN DE INSIGHTS ============

  editarInsight(insight: AIInsight): void {
    this.editingInsightId = insight.id;
    this.originalInsight = { ...insight };
    this.editedTitle = insight.title;
    this.editedDescription = insight.description;
    this.editedRecommendation = insight.recommendation || '';
  }

  cancelarEdicion(): void {
    this.editingInsightId = null;
    this.editedTitle = '';
    this.editedDescription = '';
    this.editedRecommendation = '';
    this.originalInsight = null;
  }

  guardarCambios(insight: AIInsight): void {
    if (!this.editedTitle.trim() || !this.editedDescription.trim()) {
      this.showAlert('El título y descripción no pueden estar vacíos', 'alert-warning');
      return;
    }

    const updateData = {
      title: this.editedTitle.trim(),
      description: this.editedDescription.trim(),
      recommendation: this.editedRecommendation.trim() || null
    };

    this.insightsService.updateInsight(insight.id, updateData)
      .pipe(
        catchError(err => {
          console.error('Error actualizando insight:', err);
          this.showAlert('Error al guardar los cambios', 'alert-danger');
          return of(null);
        })
      )
      .subscribe(updated => {
        if (updated) {
          // Actualizar el insight en la lista local
          const index = this.insights.findIndex(i => i.id === insight.id);
          if (index !== -1) {
            this.insights[index] = { ...this.insights[index], ...updateData };
          }
          this.showAlert('Insight actualizado correctamente', 'alert-success');
          this.cancelarEdicion();
        }
      });
  }

  estaEditando(insightId: string): boolean {
    return this.editingInsightId === insightId;
  }

  // ============ EXPORTACIÓN A WORD ============

  async exportarAWord(): Promise<void> {
    if (this.insights.length === 0) {
      this.showAlert('No hay insights para exportar', 'alert-warning');
      return;
    }

    // Verificar si las librerías están disponibles
    try {
      // Intentar importar las librerías
      const docx = await import('docx');
      const fileSaver = await import('file-saver');
      
      // Si llegamos aquí, las librerías están disponibles
      await this.generarDocumentoWord(docx, fileSaver);
    } catch (error: any) {
      console.error('Error al cargar librerías de exportación:', error);
      
      // Si falla la importación, ofrecer alternativa
      this.showAlert('La exportación a Word no está disponible en este momento. Por favor, copia el contenido manualmente.', 'alert-warning');
    }
  }

  private async generarDocumentoWord(docx: any, fileSaver: any): Promise<void> {
    try {
      const { Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType } = docx;
      const { saveAs } = fileSaver;

      const doc = new Document({
        sections: [{
          properties: {},
          children: [
            // Título del documento
            new Paragraph({
              text: 'AI Insights — Análisis Agile',
              heading: HeadingLevel.HEADING_1,
              alignment: AlignmentType.CENTER,
              spacing: { after: 400 }
            }),
            new Paragraph({
              children: [
                new TextRun({
                  text: `Proyecto: ${this.proyecto?.nombre || 'Sin nombre'}`,
                  bold: true
                })
              ],
              spacing: { after: 200 }
            }),
            new Paragraph({
              children: [
                new TextRun({
                  text: `Fecha de exportación: ${new Date().toLocaleDateString('es-AR')}`,
                  italics: true
                })
              ],
              spacing: { after: 400 }
            }),
            new Paragraph({
              text: `Total de insights: ${this.insights.length}`,
              spacing: { after: 600 }
            }),

            // Insights
            ...this.insights.flatMap((insight, index) => [
              new Paragraph({
                text: `${index + 1}. ${insight.title}`,
                heading: HeadingLevel.HEADING_2,
                spacing: { before: 400, after: 200 }
              }),
              new Paragraph({
                children: [
                  new TextRun({ text: 'Tipo: ', bold: true }),
                  new TextRun(this.getTypeLabel(insight.type))
                ],
                spacing: { after: 100 }
              }),
              new Paragraph({
                children: [
                  new TextRun({ text: 'Severidad: ', bold: true }),
                  new TextRun(this.getSeverityLabel(insight.severity))
                ],
                spacing: { after: 100 }
              }),
              new Paragraph({
                children: [
                  new TextRun({ text: 'Confianza: ', bold: true }),
                  new TextRun(this.getConfidenceLabel(insight.confidence))
                ],
                spacing: { after: 200 }
              }),
              new Paragraph({
                text: 'Observación',
                heading: HeadingLevel.HEADING_3,
                spacing: { after: 100 }
              }),
              new Paragraph({
                text: insight.description,
                spacing: { after: 200 }
              }),

              // Evidencia
              ...(insight.evidence && insight.evidence.length > 0 ? [
                new Paragraph({
                  text: 'Evidencia',
                  heading: HeadingLevel.HEADING_3,
                  spacing: { after: 100 }
                }),
                ...insight.evidence.flatMap(evidence => [
                  new Paragraph({
                    children: [
                      new TextRun({ text: `${evidence.categoria}: `, bold: true }),
                      new TextRun(this.formatEvidenceForWord(evidence))
                    ],
                    spacing: { after: 100 }
                  })
                ])
              ] : []),

              // Recomendación
              ...(insight.recommendation ? [
                new Paragraph({
                  text: 'Recomendación',
                  heading: HeadingLevel.HEADING_3,
                  spacing: { after: 100, before: 200 }
                }),
                new Paragraph({
                  text: insight.recommendation,
                  spacing: { after: 200 }
                })
              ] : []),

              new Paragraph({
                children: [
                  new TextRun({
                    text: `Generado: ${new Date(insight.createdAt).toLocaleString('es-AR')}`,
                    italics: true,
                    size: 18
                  })
                ],
                spacing: { after: 400 }
              })
            ])
          ]
        }]
      });

      const blob = await Packer.toBlob(doc);
      const fileName = `Insights_${this.proyecto?.nombre || 'Proyecto'}_${new Date().toISOString().split('T')[0]}.docx`;
      saveAs(blob, fileName);

      this.showAlert('Documento Word exportado correctamente', 'alert-success');
    } catch (error) {
      console.error('Error generando documento Word:', error);
      this.showAlert('Error al generar el documento Word.', 'alert-danger');
    }
  }

  private formatEvidenceForWord(evidence: InsightEvidence): string {
    const parts: string[] = [];
    
    if (evidence.valorActual !== null) {
      parts.push(`Valor actual: ${evidence.valorActual.toFixed(2)}`);
    }
    if (evidence.valorAnterior !== null) {
      parts.push(`Valor anterior: ${evidence.valorAnterior.toFixed(2)}`);
    }
    if (evidence.promedioHistorico !== null) {
      parts.push(`Promedio: ${evidence.promedioHistorico.toFixed(2)}`);
    }
    if (evidence.variacionPorcentual !== null) {
      parts.push(`Variación: ${evidence.variacionPorcentual.toFixed(1)}%`);
    }
    if (evidence.tendencia) {
      parts.push(`Tendencia: ${evidence.tendencia}`);
    }
    if (evidence.numeroSprints !== null) {
      parts.push(`Sprints: ${evidence.numeroSprints}`);
    }
    
    return parts.join(', ');
  }
}
