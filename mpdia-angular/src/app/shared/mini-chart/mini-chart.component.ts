// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface PuntoMiniChart {
  fecha: string;   // ISO date/instant string
  valor: number;
}

interface PuntoDibujado {
  x: number;
  y: number;
  fechaLabel: string;
  valor: number;
  tooltip: string;
}

interface TickY {
  y: number;
  valor: string;
}

/**
 * FASE 16 — gráfica temporal reutilizable (Ejecución y Evaluación).
 *
 * Extiende el mismo patrón de SVG artesanal que ya usaba EvaluacionComponent
 * (sin ninguna librería de gráficas de terceros, ya que el proyecto no tiene
 * ninguna instalada), agregándole ejes X/Y con numeración visible — lo único
 * que faltaba. Los puntos siempre provienen de datos ya persistidos
 * (RegistroValor vía GET /api/evaluacion/proyecto/{proyectoId}/detalle):
 * este componente nunca fabrica ni simula datos, solo los dibuja.
 */
@Component({
  selector: 'app-mini-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (puntos.length === 0) {
      <div class="text-center text-muted small py-3">Sin registros todavía.</div>
    } @else {
      <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" [attr.width]="'100%'" [attr.height]="height">
        <!-- Eje Y + ticks -->
        <line [attr.x1]="padL" [attr.y1]="padT" [attr.x2]="padL" [attr.y2]="height - padB"
              stroke="var(--bs-secondary)" stroke-width="1"/>
        @for (t of ticksY; track t.y) {
          <line [attr.x1]="padL - 3" [attr.y1]="t.y" [attr.x2]="width - padR" [attr.y2]="t.y"
                stroke="var(--bs-border-color)" stroke-width="1" stroke-dasharray="2,2"/>
          <text [attr.x]="padL - 6" [attr.y]="t.y + 3" text-anchor="end" font-size="9"
                fill="var(--bs-secondary-color)">{{ t.valor }}</text>
        }

        <!-- Eje X -->
        <line [attr.x1]="padL" [attr.y1]="height - padB" [attr.x2]="width - padR" [attr.y2]="height - padB"
              stroke="var(--bs-secondary)" stroke-width="1"/>
        @for (p of puntosDibujados; track p.x) {
          <text [attr.x]="p.x" [attr.y]="height - padB + 12" text-anchor="middle" font-size="8"
                fill="var(--bs-secondary-color)">{{ p.fechaLabel }}</text>
        }

        <!-- Serie -->
        @if (puntosDibujados.length > 1) {
          <polyline [attr.points]="polylinePoints()" fill="none" stroke="var(--bs-primary)" stroke-width="2"/>
        }
        @for (p of puntosDibujados; track p.x + '-' + p.y) {
          <circle [attr.cx]="p.x" [attr.cy]="p.y" r="3.5" fill="var(--bs-primary)">
            <title>{{ p.tooltip }}</title>
          </circle>
        }
      </svg>
    }
  `
})
export class MiniChartComponent implements OnChanges {
  @Input() puntos: PuntoMiniChart[] = [];
  @Input() width = 340;
  @Input() height = 150;
  @Input() unidad = '';

  readonly padL = 32;
  readonly padR = 10;
  readonly padT = 10;
  readonly padB = 18;

  puntosDibujados: PuntoDibujado[] = [];
  ticksY: TickY[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['puntos'] || changes['width'] || changes['height']) {
      this.construir();
    }
  }

  private construir(): void {
    const ordenados = [...this.puntos].sort(
      (a, b) => new Date(a.fecha).getTime() - new Date(b.fecha).getTime()
    );

    if (ordenados.length === 0) {
      this.puntosDibujados = [];
      this.ticksY = [];
      return;
    }

    const valores = ordenados.map(p => p.valor);
    let min = Math.min(...valores);
    let max = Math.max(...valores);
    if (min === max) { min -= 1; max += 1; }

    const anchoUtil = this.width - this.padL - this.padR;
    const altoUtil = this.height - this.padT - this.padB;
    const n = ordenados.length;

    this.puntosDibujados = ordenados.map((p, i) => ({
      x: this.padL + (n === 1 ? anchoUtil / 2 : (i / (n - 1)) * anchoUtil),
      y: this.padT + altoUtil - ((p.valor - min) / (max - min)) * altoUtil,
      fechaLabel: this.formatearFecha(p.fecha),
      valor: p.valor,
      tooltip: `${this.formatearFecha(p.fecha)}: ${p.valor}${this.unidad ? ' ' + this.unidad : ''}`
    }));

    const pasos = 4;
    this.ticksY = Array.from({ length: pasos + 1 }, (_, i) => {
      const valor = min + ((max - min) * i) / pasos;
      const y = this.padT + altoUtil - (i / pasos) * altoUtil;
      return { y, valor: this.redondear(valor) };
    });
  }

  private redondear(v: number): string {
    return (Math.round(v * 100) / 100).toString();
  }

  private formatearFecha(fecha: string): string {
    const d = new Date(fecha);
    if (Number.isNaN(d.getTime())) return fecha;
    // FASE 16: fechaCaptura se construye siempre en UTC (ver EjecucionComponent);
    // formatear también en UTC evita que la zona horaria local del navegador
    // corra la fecha mostrada un día hacia atrás o adelante.
    return d.toLocaleDateString('es-CO', { day: '2-digit', month: '2-digit', timeZone: 'UTC' });
  }

  polylinePoints(): string {
    return this.puntosDibujados.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
  }
}
