import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ObjetivoMedicion } from '../models/objetivo.model';

/**
 * Servicio local para objetivos de medición (RF03/RF04).
 * Persiste en localStorage hasta que el backend Spring Boot
 * exponga el endpoint /api/objetivos.
 */
@Injectable({ providedIn: 'root' })
export class ObjetivoService {

  private readonly KEY = 'mpdia_objetivos';
  private objetivos$ = new BehaviorSubject<ObjetivoMedicion[]>(this.load());

  getAll() {
    return this.objetivos$.asObservable();
  }

  getBySprint(sprintName: string): ObjetivoMedicion[] {
    return this.objetivos$.value.filter(o => o.sprintName === sprintName);
  }

  save(obj: ObjetivoMedicion): void {
    const list = this.objetivos$.value;
    if (obj.id) {
      const idx = list.findIndex(o => o.id === obj.id);
      if (idx >= 0) list[idx] = obj;
    } else {
      obj.id = crypto.randomUUID();
      obj.creadoEn = new Date().toISOString();
      list.push(obj);
    }
    this.persist(list);
  }

  delete(id: string): void {
    const list = this.objetivos$.value.filter(o => o.id !== id);
    this.persist(list);
  }

  private persist(list: ObjetivoMedicion[]): void {
    localStorage.setItem(this.KEY, JSON.stringify(list));
    this.objetivos$.next([...list]);
  }

  private load(): ObjetivoMedicion[] {
    const raw = localStorage.getItem(this.KEY);
    return raw ? JSON.parse(raw) : [];
  }
}
