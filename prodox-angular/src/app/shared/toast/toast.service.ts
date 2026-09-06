// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'danger' | 'warning' | 'info';

export interface ToastMessage {
  id: number;
  text: string;
  type: ToastType;
}

/**
 * Servicio centralizado de notificaciones tipo toast, reutilizable desde
 * cualquier componente de la aplicación. Antes, cada página duplicaba su
 * propio patrón local (alertMsg/alertClass + showAlert() con setTimeout),
 * copiado y pegado en más de una decena de componentes — este servicio no
 * reemplaza esos banners donde ya funcionan (no se tocan para no arriesgar
 * regresiones ni duplicar el mensaje), pero es el punto único a usar para
 * cualquier notificación NUEVA, evitando seguir copiando el mismo patrón.
 *
 * <app-toast-container> (montado una sola vez en ShellComponent, ver
 * shell.component.ts) es el único lugar que renderiza los toasts activos.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private readonly _toasts = signal<ToastMessage[]>([]);
  readonly toasts = this._toasts.asReadonly();

  private readonly DURATIONS_MS: Record<ToastType, number> = {
    success: 4000,
    info: 4000,
    warning: 6000,
    danger: 7000
  };

  /**
   * Muestra un toast. Si ya hay uno activo con el mismo texto y tipo, no
   * agrega un duplicado (evita, por ejemplo, que un doble clic accidental
   * o dos llamadas equivalentes muestren el mismo mensaje dos veces).
   */
  show(text: string, type: ToastType = 'info', durationMs?: number): void {
    if (!text) return;
    if (this._toasts().some(t => t.text === text && t.type === type)) return;

    const id = this.nextId++;
    this._toasts.update(list => [...list, { id, text, type }]);

    const duration = durationMs ?? this.DURATIONS_MS[type];
    setTimeout(() => this.dismiss(id), duration);
  }

  success(text: string): void {
    this.show(text, 'success');
  }

  error(text: string): void {
    this.show(text, 'danger');
  }

  warning(text: string): void {
    this.show(text, 'warning');
  }

  info(text: string): void {
    this.show(text, 'info');
  }

  dismiss(id: number): void {
    this._toasts.update(list => list.filter(t => t.id !== id));
  }
}
