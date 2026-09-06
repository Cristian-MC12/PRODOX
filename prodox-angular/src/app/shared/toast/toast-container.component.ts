// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService, ToastType } from './toast.service';

/**
 * Renderiza los toasts activos de ToastService. Se monta UNA sola vez, en
 * ShellComponent (ver shell.component.ts) — como todas las páginas ya usan
 * <app-shell>, cualquier componente puede inyectar ToastService y llamar
 * success()/error()/warning()/info() sin tener que montar su propio
 * contenedor. Usa las clases nativas de Bootstrap 5 (.toast, .toast-body,
 * .btn-close) ya disponibles en el proyecto — sin librería nueva y sin
 * depender del JS de Bootstrap: la visibilidad la controla directamente el
 * @for sobre la señal de toasts activos.
 */
@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-stack position-fixed bottom-0 end-0 p-3" style="z-index: 1080;" aria-live="polite" aria-atomic="true">
      @for (t of toastService.toasts(); track t.id) {
        <div class="toast show align-items-center text-white border-0 mb-2 shadow" [class]="bgClass(t.type)" role="status">
          <div class="d-flex">
            <div class="toast-body">{{ t.text }}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto"
                    (click)="toastService.dismiss(t.id)" aria-label="Cerrar"></button>
          </div>
        </div>
      }
    </div>
  `
})
export class ToastContainerComponent {
  constructor(public toastService: ToastService) {}

  bgClass(type: ToastType): string {
    return ({
      success: 'bg-success',
      danger: 'bg-danger',
      warning: 'bg-warning text-dark',
      info: 'bg-info text-dark'
    } as Record<ToastType, string>)[type];
  }
}
