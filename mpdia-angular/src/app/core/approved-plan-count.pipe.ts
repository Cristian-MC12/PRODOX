import { Pipe, PipeTransform } from '@angular/core';
import { MetricaPlan } from '../models/metrica-plan.model';

@Pipe({ name: 'approvedPlanCount', standalone: true })
export class ApprovedPlanCountPipe implements PipeTransform {
  transform(metricas: MetricaPlan[]): number {
    return metricas.filter(m => m.status === 'aprobada').length;
  }
}
